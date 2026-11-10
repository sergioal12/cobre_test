package com.cobre.notification.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain model representing a notification that exhausted all retry attempts (max 5 retries)
 * and was moved to the Dead Letter Queue (DLQ) table with all context required for manual reprocessing.
 */
public record DeadLetterNotification(
        Long id,
        String eventId,
        String clientId,
        String eventType,
        String payloadContent,
        String targetWebhookUrl,
        int retryAttemptsExhausted,
        Integer lastHttpStatusCode,
        String failureReason,
        DlqStatus status,
        Instant createdAt,
        Instant lastReprocessedAt
) {
    public DeadLetterNotification {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(payloadContent, "payloadContent must not be null");
        status = status != null ? status : DlqStatus.PENDING_MANUAL_REPROCESS;
        createdAt = createdAt != null ? createdAt : Instant.now();
    }
}

