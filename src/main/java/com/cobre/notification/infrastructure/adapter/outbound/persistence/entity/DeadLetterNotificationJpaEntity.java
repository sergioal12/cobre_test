package com.cobre.notification.infrastructure.adapter.outbound.persistence.entity;

import com.cobre.notification.domain.model.DlqStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing the Dead Letter Queue (DLQ) table ('notification_dlq')
 * where notifications that failed after 5 retry attempts are stored with all context
 * required for manual reprocessing.
 */
@Entity
@Table(
        name = "notification_dlq",
        indexes = {
                @Index(name = "idx_notification_dlq_event_id", columnList = "event_id", unique = true),
                @Index(name = "idx_notification_dlq_client_status", columnList = "client_id, status")
        }
)
public class DeadLetterNotificationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 64, nullable = false, unique = true)
    private String eventId;

    @Column(name = "client_id", length = 64, nullable = false)
    private String clientId;

    @Column(name = "event_type", length = 100, nullable = false)
    private String eventType;

    @Column(name = "payload_content", length = 2000, nullable = false)
    private String payloadContent;

    @Column(name = "target_webhook_url", length = 512, nullable = false)
    private String targetWebhookUrl;

    @Column(name = "retry_attempts_exhausted", nullable = false)
    private int retryAttemptsExhausted;

    @Column(name = "last_http_status_code")
    private Integer lastHttpStatusCode;

    @Column(name = "failure_reason", length = 1000, nullable = false)
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 48, nullable = false)
    private DlqStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_reprocessed_at")
    private Instant lastReprocessedAt;

    public DeadLetterNotificationJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayloadContent() {
        return payloadContent;
    }

    public void setPayloadContent(String payloadContent) {
        this.payloadContent = payloadContent;
    }

    public String getTargetWebhookUrl() {
        return targetWebhookUrl;
    }

    public void setTargetWebhookUrl(String targetWebhookUrl) {
        this.targetWebhookUrl = targetWebhookUrl;
    }

    public int getRetryAttemptsExhausted() {
        return retryAttemptsExhausted;
    }

    public void setRetryAttemptsExhausted(int retryAttemptsExhausted) {
        this.retryAttemptsExhausted = retryAttemptsExhausted;
    }

    public Integer getLastHttpStatusCode() {
        return lastHttpStatusCode;
    }

    public void setLastHttpStatusCode(Integer lastHttpStatusCode) {
        this.lastHttpStatusCode = lastHttpStatusCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public DlqStatus getStatus() {
        return status;
    }

    public void setStatus(DlqStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastReprocessedAt() {
        return lastReprocessedAt;
    }

    public void setLastReprocessedAt(Instant lastReprocessedAt) {
        this.lastReprocessedAt = lastReprocessedAt;
    }
}

