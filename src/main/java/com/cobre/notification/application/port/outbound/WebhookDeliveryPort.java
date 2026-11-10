package com.cobre.notification.application.port.outbound;

import com.cobre.notification.domain.model.NotificationEvent;

/**
 * Outbound Port for dispatching notification payloads over HTTPS to a client's webhook endpoint.
 */
public interface WebhookDeliveryPort {

    WebhookDeliveryResult deliver(NotificationEvent event, String targetUrl);

    record WebhookDeliveryResult(
            boolean success,
            Integer httpStatusCode,
            long latencyMs,
            String errorMessage
    ) {
    }
}

