package com.cobre.notification.application.port.inbound;

import com.cobre.notification.domain.model.NotificationEvent;

/**
 * Inbound Port (Use Case) that registers a new notification event for the authenticated client
 * and dispatches it to the client's webhook.
 */
public interface CreateNotificationEventUseCase {

    NotificationEvent createAndDispatch(CreateEventCommand command);

    /**
     * @param clientId   authenticated client that owns the event (never taken from the payload)
     * @param eventId    optional; generated when blank
     * @param webhookUrl optional override of the client's webhook endpoint (validated before use)
     */
    record CreateEventCommand(
            String clientId,
            String eventId,
            String eventType,
            String content,
            String webhookUrl
    ) {
    }
}

