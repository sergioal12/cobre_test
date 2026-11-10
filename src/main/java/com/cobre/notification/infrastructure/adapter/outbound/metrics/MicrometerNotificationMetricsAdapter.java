package com.cobre.notification.infrastructure.adapter.outbound.metrics;

import com.cobre.notification.application.port.outbound.NotificationMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Outbound adapter exposing near real-time notification delivery metrics to Micrometer / Prometheus.
 */
@Component
public class MicrometerNotificationMetricsAdapter implements NotificationMetricsPort {

    private final MeterRegistry meterRegistry;

    public MicrometerNotificationMetricsAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordDeliverySuccess(String clientId, String eventType, long latencyMs) {
        Counter.builder("cobre.notification.delivery.total")
                .description("Total notification webhook delivery attempts")
                .tag("status", "success")
                .tag("client_id", clientId)
                .tag("event_type", eventType)
                .register(meterRegistry)
                .increment();

        Timer.builder("cobre.notification.delivery.latency")
                .description("Webhook notification delivery latency")
                .tag("status", "success")
                .tag("client_id", clientId)
                .register(meterRegistry)
                .record(latencyMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordDeliveryFailure(String clientId, String eventType, String reason, long latencyMs) {
        Counter.builder("cobre.notification.delivery.total")
                .description("Total notification webhook delivery attempts")
                .tag("status", "failure")
                .tag("client_id", clientId)
                .tag("event_type", eventType)
                .tag("reason", reason != null ? reason : "unknown")
                .register(meterRegistry)
                .increment();

        Timer.builder("cobre.notification.delivery.latency")
                .description("Webhook notification delivery latency")
                .tag("status", "failure")
                .tag("client_id", clientId)
                .register(meterRegistry)
                .record(latencyMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordReplayRequested(String clientId, String eventId) {
        Counter.builder("cobre.notification.replay.total")
                .description("Total manual notification replay requests")
                .tag("client_id", clientId)
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void recordMovedToDlq(String clientId, String eventType, String eventId) {
        Counter.builder("cobre.notification.dlq.total")
                .description("Total notifications moved to Dead Letter Queue (DLQ) after exhausting max retries")
                .tag("client_id", clientId)
                .tag("event_type", eventType)
                .register(meterRegistry)
                .increment();
    }
}
