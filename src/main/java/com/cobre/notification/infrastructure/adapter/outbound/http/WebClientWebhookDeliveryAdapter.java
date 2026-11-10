package com.cobre.notification.infrastructure.adapter.outbound.http;

import com.cobre.notification.application.port.outbound.WebhookDeliveryPort;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.infrastructure.config.NotificationProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Outbound adapter that delivers notification events over HTTPS using Spring WebClient.
 */
@Component
public class WebClientWebhookDeliveryAdapter implements WebhookDeliveryPort {

    public static final String HEADER_SIGNATURE = "X-Cobre-Signature";
    public static final String HEADER_TIMESTAMP = "X-Cobre-Timestamp";
    public static final String HEADER_EVENT_ID = "X-Cobre-Event-Id";
    public static final String HEADER_CLIENT_ID = "X-Cobre-Client-Id";

    private final WebClient webClient;
    private final WebhookUrlSecurityValidator urlSecurityValidator;
    private final HmacSignatureGenerator hmacSignatureGenerator;
    private final ObjectMapper objectMapper;
    private final NotificationProperties properties;

    public WebClientWebhookDeliveryAdapter(
            WebClient webClient,
            WebhookUrlSecurityValidator urlSecurityValidator,
            HmacSignatureGenerator hmacSignatureGenerator,
            ObjectMapper objectMapper,
            NotificationProperties properties
    ) {
        this.webClient = webClient;
        this.urlSecurityValidator = urlSecurityValidator;
        this.hmacSignatureGenerator = hmacSignatureGenerator;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public WebhookDeliveryResult deliver(NotificationEvent event, String targetUrl) {
        long startNanos = System.nanoTime();
        try {
            urlSecurityValidator.validateTargetUrl(targetUrl);

            WebhookOutboundPayload outboundPayload = new WebhookOutboundPayload(
                    event.getEventId(),
                    event.getEventType(),
                    event.getContent(),
                    event.getDeliveryDate().toString(),
                    event.getClientId()
            );

            String payloadJson = objectMapper.writeValueAsString(outboundPayload);
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String signature = hmacSignatureGenerator.signPayload(timestamp, payloadJson);

            Duration timeout = Duration.ofMillis(properties.getWebhook().getReadTimeoutMs());

            HttpStatusCode statusCode = webClient.post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HEADER_EVENT_ID, event.getEventId())
                    .header(HEADER_CLIENT_ID, event.getClientId())
                    .header(HEADER_TIMESTAMP, timestamp)
                    .header(HEADER_SIGNATURE, signature)
                    .bodyValue(payloadJson)
                    .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode()))
                    .timeout(timeout)
                    .block();

            long latencyMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            if (statusCode != null && statusCode.is2xxSuccessful()) {
                return new WebhookDeliveryResult(true, statusCode.value(), latencyMs, null);
            }

            int rawStatus = statusCode != null ? statusCode.value() : 500;
            return new WebhookDeliveryResult(
                    false,
                    rawStatus,
                    latencyMs,
                    "Webhook endpoint responded with HTTP " + rawStatus
            );
        } catch (Exception ex) {
            long latencyMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            return new WebhookDeliveryResult(false, null, latencyMs, ex.getMessage());
        }
    }

    public record WebhookOutboundPayload(
            @JsonProperty("event_id") String eventId,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("content") String content,
            @JsonProperty("delivery_date") String deliveryDate,
            @JsonProperty("client_id") String clientId
    ) {
    }
}

