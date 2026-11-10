package com.cobre.notification.infrastructure.adapter.outbound.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "delivery_attempts",
        indexes = {
                @Index(name = "idx_delivery_attempts_event_id", columnList = "event_id")
        }
)
public class DeliveryAttemptJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private NotificationEventJpaEntity notificationEvent;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    public DeliveryAttemptJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public NotificationEventJpaEntity getNotificationEvent() {
        return notificationEvent;
    }

    public void setNotificationEvent(NotificationEventJpaEntity notificationEvent) {
        this.notificationEvent = notificationEvent;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(Integer httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public void setAttemptedAt(Instant attemptedAt) {
        this.attemptedAt = attemptedAt;
    }
}

