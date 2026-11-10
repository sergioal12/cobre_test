package com.cobre.notification.infrastructure.adapter.outbound.persistence.entity;

import com.cobre.notification.domain.model.DeliveryStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "notification_events",
        indexes = {
                @Index(name = "idx_notification_events_client_id", columnList = "client_id"),
                @Index(name = "idx_notification_events_client_status_date", columnList = "client_id, delivery_status, delivery_date")
        }
)
public class NotificationEventJpaEntity {

    @Id
    @Column(name = "event_id", length = 64, nullable = false)
    private String eventId;

    @Column(name = "event_type", length = 100, nullable = false)
    private String eventType;

    @Column(name = "content", length = 1000, nullable = false)
    private String content;

    @Column(name = "delivery_date", nullable = false)
    private Instant deliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", length = 32, nullable = false)
    private DeliveryStatus deliveryStatus;

    @Column(name = "client_id", length = 64, nullable = false)
    private String clientId;

    @Column(name = "webhook_url", length = 512)
    private String webhookUrl;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error_message", length = 1000)
    private String lastErrorMessage;

    @OneToMany(
            mappedBy = "notificationEvent",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER
    )
    @OrderBy("attemptNumber ASC")
    private List<DeliveryAttemptJpaEntity> deliveryAttempts = new ArrayList<>();

    public NotificationEventJpaEntity() {
    }

    public void addDeliveryAttempt(DeliveryAttemptJpaEntity attempt) {
        attempt.setNotificationEvent(this);
        this.deliveryAttempts.add(attempt);
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(Instant deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
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

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public List<DeliveryAttemptJpaEntity> getDeliveryAttempts() {
        return deliveryAttempts;
    }

    public void setDeliveryAttempts(List<DeliveryAttemptJpaEntity> deliveryAttempts) {
        this.deliveryAttempts = deliveryAttempts;
    }
}

