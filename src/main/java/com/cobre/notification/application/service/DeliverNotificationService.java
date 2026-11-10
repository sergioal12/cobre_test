package com.cobre.notification.application.service;

import com.cobre.notification.application.port.inbound.DeliverNotificationUseCase;
import com.cobre.notification.application.port.outbound.DeadLetterQueuePort;
import com.cobre.notification.application.port.outbound.NotificationEventRepositoryPort;
import com.cobre.notification.application.port.outbound.NotificationMetricsPort;
import com.cobre.notification.application.port.outbound.SubscriptionPort;
import com.cobre.notification.application.port.outbound.WebhookDeliveryPort;
import com.cobre.notification.application.port.outbound.WebhookDeliveryPort.WebhookDeliveryResult;
import com.cobre.notification.domain.model.DeadLetterNotification;
import com.cobre.notification.domain.model.DlqStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.model.WebhookSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Use case implementation that validates client subscription ownership, delivers the notification
 * over HTTPS to the client's webhook endpoint with exponential backoff + jitter retry strategy
 * (maximum 5 retry attempts by default), and upon exhausting all 5 attempts moves the event
 * to the Dead Letter Queue (DLQ) table with all context needed for manual reprocessing.
 */
@Service
public class DeliverNotificationService implements DeliverNotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeliverNotificationService.class);

    private final NotificationEventRepositoryPort eventRepositoryPort;
    private final SubscriptionPort subscriptionPort;
    private final WebhookDeliveryPort webhookDeliveryPort;
    private final DeadLetterQueuePort deadLetterQueuePort;
    private final NotificationMetricsPort metricsPort;
    private final int maxRetryAttempts;
    private final long initialBackoffMs;

    public DeliverNotificationService(
            NotificationEventRepositoryPort eventRepositoryPort,
            SubscriptionPort subscriptionPort,
            WebhookDeliveryPort webhookDeliveryPort,
            DeadLetterQueuePort deadLetterQueuePort,
            NotificationMetricsPort metricsPort,
            @Value("${cobre.notification.webhook.max-attempts:5}") int maxRetryAttempts,
            @Value("${cobre.notification.webhook.initial-backoff-ms:200}") long initialBackoffMs
    ) {
        this.eventRepositoryPort = eventRepositoryPort;
        this.subscriptionPort = subscriptionPort;
        this.webhookDeliveryPort = webhookDeliveryPort;
        this.deadLetterQueuePort = deadLetterQueuePort;
        this.metricsPort = metricsPort;
        this.maxRetryAttempts = Math.max(1, maxRetryAttempts);
        this.initialBackoffMs = Math.max(0L, initialBackoffMs);
    }

    @Override
    public NotificationEvent deliverNotification(NotificationEvent event) {
        // 1. Confirm with subscription if event has to be delivered and belongs to that client
        Optional<WebhookSubscription> subscriptionOpt =
                subscriptionPort.findActiveSubscriptionByClientId(event.getClientId());

        if (subscriptionOpt.isEmpty()
                || !subscriptionOpt.get().shouldDeliverEvent(event.getClientId(), event.getEventType())) {
            String reason = "No active webhook subscription confirmed for client '"
                    + event.getClientId() + "' and event_type '" + event.getEventType() + "'";
            log.warn("Skipping delivery for event {}: {}", event.getEventId(), reason);
            event.markAsDefinitelyFailed(reason, Instant.now());
            metricsPort.recordDeliveryFailure(event.getClientId(), event.getEventType(), "no_active_subscription", 0L);
            NotificationEvent saved = eventRepositoryPort.save(event);
            sendToDeadLetterQueue(saved, null, 0, reason);
            return saved;
        }

        WebhookSubscription subscription = subscriptionOpt.get();
        String targetUrl = subscription.webhookUrl();
        event.setWebhookUrl(targetUrl);

        Integer lastStatusCode = null;
        String lastFailureReason = null;

        // 2. Deliver notification with Exponential Backoff + Jitter retry strategy (max 5 attempts)
        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            WebhookDeliveryResult result = webhookDeliveryPort.deliver(event, targetUrl);

            if (result.success()) {
                int statusCode = result.httpStatusCode() != null ? result.httpStatusCode() : 200;
                Instant deliveredAt = Instant.now();
                event.recordSuccessfulDelivery(statusCode, result.latencyMs(), deliveredAt);
                deadLetterQueuePort.markAsReprocessedIfPresent(event.getEventId(), deliveredAt);
                metricsPort.recordDeliverySuccess(event.getClientId(), event.getEventType(), result.latencyMs());
                log.info("Delivered event {} to client {} on attempt {}/{} (HTTP {}, {} ms)",
                        event.getEventId(), event.getClientId(), attempt, maxRetryAttempts, statusCode, result.latencyMs());
                return eventRepositoryPort.save(event);
            }

            lastStatusCode = result.httpStatusCode();
            lastFailureReason = result.errorMessage();

            // Record failed attempt in audit trail
            event.recordFailedAttempt(
                    result.httpStatusCode(),
                    result.latencyMs(),
                    result.errorMessage(),
                    Instant.now(),
                    maxRetryAttempts,
                    attempt
            );
            metricsPort.recordDeliveryFailure(
                    event.getClientId(),
                    event.getEventType(),
                    result.httpStatusCode() != null ? "http_" + result.httpStatusCode() : "network_error",
                    result.latencyMs()
            );

            log.warn("Failed delivery attempt {}/{} for event {} to client {}: {}",
                    attempt, maxRetryAttempts, event.getEventId(), event.getClientId(), result.errorMessage());

            if (attempt < maxRetryAttempts) {
                eventRepositoryPort.save(event);
                sleepWithExponentialBackoffAndJitter(attempt);
            }
        }

        // 3. Exhausted all 5 retry attempts -> mark as definitely FAILED and persist in DLQ table for manual reprocessing
        String finalFailureReason = lastFailureReason != null
                ? "Exhausted " + maxRetryAttempts + " retry attempts. Last error: " + lastFailureReason
                : "Exhausted maximum webhook delivery attempts (" + maxRetryAttempts + ")";

        event.markAsDefinitelyFailed(finalFailureReason, Instant.now());
        NotificationEvent failedEvent = eventRepositoryPort.save(event);

        sendToDeadLetterQueue(failedEvent, lastStatusCode, maxRetryAttempts, finalFailureReason);
        return failedEvent;
    }

    private void sendToDeadLetterQueue(
            NotificationEvent failedEvent,
            Integer lastStatusCode,
            int attemptsExhausted,
            String failureReason
    ) {
        DeadLetterNotification dlqEntry = new DeadLetterNotification(
                null,
                failedEvent.getEventId(),
                failedEvent.getClientId(),
                failedEvent.getEventType(),
                failedEvent.getContent(),
                failedEvent.getWebhookUrl() != null ? failedEvent.getWebhookUrl() : "UNCONFIGURED",
                attemptsExhausted,
                lastStatusCode,
                failureReason,
                DlqStatus.PENDING_MANUAL_REPROCESS,
                Instant.now(),
                null
        );
        deadLetterQueuePort.saveForManualReprocessing(dlqEntry);
        metricsPort.recordMovedToDlq(failedEvent.getClientId(), failedEvent.getEventType(), failedEvent.getEventId());
        log.error("Event {} for client {} moved to Dead Letter Queue (notification_dlq table) after {} failed attempts.",
                failedEvent.getEventId(), failedEvent.getClientId(), attemptsExhausted);
    }

    private void sleepWithExponentialBackoffAndJitter(int attempt) {
        if (initialBackoffMs <= 0) {
            return;
        }
        long exponentialDelay = initialBackoffMs * (1L << (attempt - 1));
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1L, exponentialDelay / 3));
        try {
            Thread.sleep(exponentialDelay + jitter);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
