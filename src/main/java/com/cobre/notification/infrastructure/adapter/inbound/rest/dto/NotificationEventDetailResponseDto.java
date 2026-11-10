package com.cobre.notification.infrastructure.adapter.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationEventDetailResponseDto(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("content") String content,
        @JsonProperty("delivery_date") Instant deliveryDate,
        @JsonProperty("delivery_status") String deliveryStatus,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("webhook_url") String webhookUrl,
        @JsonProperty("attempt_count") int attemptCount,
        @JsonProperty("last_error_message") String lastErrorMessage,
        @JsonProperty("delivery_attempts") List<DeliveryAttemptDto> deliveryAttempts
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeliveryAttemptDto(
            @JsonProperty("attempt_number") int attemptNumber,
            @JsonProperty("http_status_code") Integer httpStatusCode,
            @JsonProperty("success") boolean success,
            @JsonProperty("latency_ms") long latencyMs,
            @JsonProperty("error_message") String errorMessage,
            @JsonProperty("attempted_at") Instant attemptedAt
    ) {
    }
}

