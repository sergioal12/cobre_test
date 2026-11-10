package com.cobre.notification.domain.model;

import java.time.Instant;

/**
 * Immutable audit record for each webhook delivery attempt.
 */
public record DeliveryAttempt(
        int attemptNumber,
        Integer httpStatusCode,
        boolean success,
        long latencyMs,
        String errorMessage,
        Instant attemptedAt
) {
}

