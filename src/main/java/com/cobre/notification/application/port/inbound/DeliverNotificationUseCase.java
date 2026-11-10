package com.cobre.notification.application.port.inbound;

import com.cobre.notification.domain.model.NotificationEvent;

/**
 * Inbound Port (Use Case) for processing and delivering an event notification via HTTPS webhook.
 */
public interface DeliverNotificationUseCase {

    NotificationEvent deliverNotification(NotificationEvent event);
}

