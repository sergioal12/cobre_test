package com.cobre.notification.application.port.outbound;

/**
 * Outbound Port for emitting near real-time observability metrics on notification deliveries.
 */
public interface NotificationMetricsPort {

    void recordDeliverySuccess(String clientId, String eventType, long latencyMs);

    void recordDeliveryFailure(String clientId, String eventType, String reason, long latencyMs);

    void recordReplayRequested(String clientId, String eventId);

    default void recordMovedToDlq(String clientId, String eventType, String eventId) {
    }
}
