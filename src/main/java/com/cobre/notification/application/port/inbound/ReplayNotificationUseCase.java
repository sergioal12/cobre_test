package com.cobre.notification.application.port.inbound;

import com.cobre.notification.domain.model.NotificationEvent;

/**
 * Inbound Port (Use Case) for re-sending a notification event whose delivery has definitely failed.
 */
public interface ReplayNotificationUseCase {

    NotificationEvent replayFailedNotification(String eventId, String clientId, String overrideWebhookUrl);
}
