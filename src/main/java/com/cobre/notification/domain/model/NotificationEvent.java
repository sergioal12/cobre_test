package com.cobre.notification.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Core Domain Aggregate representing a platform notification event generated for a specific client.
 * Zero framework dependencies (Hexagonal Architecture).
 */
public class NotificationEvent {

    public static final int DEFAULT_MAX_RETRY_ATTEMPTS = 5;

    private final String eventId;
    private final String eventType;
    private final String content;
    private final String clientId;
    private Instant deliveryDate;
    private DeliveryStatus deliveryStatus;
    private String webhookUrl;
    private int attemptCount;
    private String lastErrorMessage;
    private final List<DeliveryAttempt> deliveryAttempts;

    public NotificationEvent(
            String eventId,
            String eventType,
            String content,
            Instant deliveryDate,
            DeliveryStatus deliveryStatus,
            String clientId,
            String webhookUrl,
            int attemptCount,
            String lastErrorMessage,
            List<DeliveryAttempt> deliveryAttempts
    ) {
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.clientId = Objects.requireNonNull(clientId, "clientId must not be null");
        this.deliveryDate = deliveryDate != null ? deliveryDate : Instant.now();
        this.deliveryStatus = deliveryStatus != null ? deliveryStatus : DeliveryStatus.PENDING;
        this.webhookUrl = webhookUrl;
        this.attemptCount = Math.max(0, attemptCount);
        this.lastErrorMessage = lastErrorMessage;
        this.deliveryAttempts = deliveryAttempts != null
                ? new ArrayList<>(deliveryAttempts)
                : new ArrayList<>();
    }

    public boolean belongsToClient(String targetClientId) {
        return this.clientId.equals(targetClientId);
    }

    public boolean canBeReplayed() {
        return this.deliveryStatus == DeliveryStatus.FAILED;
    }

    public void prepareForReplay() {
        if (!canBeReplayed()) {
            throw new IllegalStateException(
                    "Only events in FAILED status can be replayed. Current status: " + this.deliveryStatus.getValue()
            );
        }
        this.deliveryStatus = DeliveryStatus.PENDING;
        this.lastErrorMessage = null;
    }

    public void recordSuccessfulDelivery(int httpStatusCode, long latencyMs, Instant deliveredAt) {
        this.attemptCount++;
        this.deliveryStatus = DeliveryStatus.COMPLETED;
        this.deliveryDate = deliveredAt != null ? deliveredAt : Instant.now();
        this.lastErrorMessage = null;
        this.deliveryAttempts.add(new DeliveryAttempt(
                this.attemptCount,
                httpStatusCode,
                true,
                latencyMs,
                null,
                this.deliveryDate
        ));
    }

    public void recordFailedAttempt(
            Integer httpStatusCode,
            long latencyMs,
            String errorMessage,
            Instant attemptedAt,
            int maxRetryAttempts,
            int currentCycleAttempt
    ) {
        this.attemptCount++;
        Instant timestamp = attemptedAt != null ? attemptedAt : Instant.now();
        this.deliveryDate = timestamp;
        this.lastErrorMessage = errorMessage;
        this.deliveryStatus = currentCycleAttempt >= maxRetryAttempts
                ? DeliveryStatus.FAILED
                : DeliveryStatus.RETRYING;
        this.deliveryAttempts.add(new DeliveryAttempt(
                this.attemptCount,
                httpStatusCode,
                false,
                latencyMs,
                errorMessage,
                timestamp
        ));
    }

    public void markAsDefinitelyFailed(String reason, Instant failedAt) {
        this.deliveryStatus = DeliveryStatus.FAILED;
        this.deliveryDate = failedAt != null ? failedAt : Instant.now();
        this.lastErrorMessage = reason;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getContent() {
        return content;
    }

    public String getClientId() {
        return clientId;
    }

    public Instant getDeliveryDate() {
        return deliveryDate;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public List<DeliveryAttempt> getDeliveryAttempts() {
        return Collections.unmodifiableList(deliveryAttempts);
    }
}
