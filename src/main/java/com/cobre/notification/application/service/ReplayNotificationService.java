package com.cobre.notification.application.service;

import com.cobre.notification.application.port.inbound.DeliverNotificationUseCase;
import com.cobre.notification.application.port.inbound.ManageWebhookSubscriptionUseCase;
import com.cobre.notification.application.port.inbound.ReplayNotificationUseCase;
import com.cobre.notification.application.port.outbound.NotificationEventRepositoryPort;
import com.cobre.notification.application.port.outbound.NotificationMetricsPort;
import com.cobre.notification.domain.exception.EventNotFoundException;
import com.cobre.notification.domain.exception.InvalidReplayStateException;
import com.cobre.notification.domain.exception.UnauthorizedClientAccessException;
import com.cobre.notification.domain.model.NotificationEvent;
import org.springframework.stereotype.Service;

/**
 * Use case implementation to re-send a notification event when its delivery has definitely failed.
 */
@Service
public class ReplayNotificationService implements ReplayNotificationUseCase {

    private final NotificationEventRepositoryPort eventRepositoryPort;
    private final DeliverNotificationUseCase deliverNotificationUseCase;
    private final ManageWebhookSubscriptionUseCase manageWebhookSubscriptionUseCase;
    private final NotificationMetricsPort metricsPort;

    public ReplayNotificationService(
            NotificationEventRepositoryPort eventRepositoryPort,
            DeliverNotificationUseCase deliverNotificationUseCase,
            ManageWebhookSubscriptionUseCase manageWebhookSubscriptionUseCase,
            NotificationMetricsPort metricsPort
    ) {
        this.eventRepositoryPort = eventRepositoryPort;
        this.deliverNotificationUseCase = deliverNotificationUseCase;
        this.manageWebhookSubscriptionUseCase = manageWebhookSubscriptionUseCase;
        this.metricsPort = metricsPort;
    }

    @Override
    public NotificationEvent replayFailedNotification(String eventId, String clientId, String overrideWebhookUrl) {
        if (clientId == null || clientId.isBlank()) {
            throw new UnauthorizedClientAccessException("Client identification (client_id) is required");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be null or blank");
        }

        NotificationEvent event = eventRepositoryPort.findByIdAndClientId(eventId.trim(), clientId.trim())
                .orElseThrow(() -> new EventNotFoundException(eventId.trim(), clientId.trim()));

        if (!event.canBeReplayed()) {
            throw new InvalidReplayStateException(
                    event.getEventId(),
                    event.getDeliveryStatus().getValue()
            );
        }

        if (overrideWebhookUrl != null && !overrideWebhookUrl.isBlank()) {
            manageWebhookSubscriptionUseCase.updateWebhookUrl(clientId.trim(), overrideWebhookUrl.trim());
        }

        event.prepareForReplay();
        eventRepositoryPort.save(event);
        metricsPort.recordReplayRequested(event.getClientId(), event.getEventId());

        return deliverNotificationUseCase.deliverNotification(event);
    }
}
