package com.cobre.notification.application.port.outbound;

import com.cobre.notification.domain.model.DeadLetterNotification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound Port for storing and querying Dead Letter Queue (DLQ) entries
 * when a notification fails after exhausting all retry attempts (max 5 retries).
 */
public interface DeadLetterQueuePort {

    DeadLetterNotification saveForManualReprocessing(DeadLetterNotification dlqNotification);

    void markAsReprocessedIfPresent(String eventId, Instant reprocessedAt);

    Optional<DeadLetterNotification> findByEventId(String eventId);

    List<DeadLetterNotification> findByClientId(String clientId);
}

