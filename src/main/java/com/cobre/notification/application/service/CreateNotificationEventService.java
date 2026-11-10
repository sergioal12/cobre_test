package com.cobre.notification.application.service;

import com.cobre.notification.application.port.inbound.CreateNotificationEventUseCase;
import com.cobre.notification.application.port.inbound.DeliverNotificationUseCase;
import com.cobre.notification.application.port.inbound.ManageWebhookSubscriptionUseCase;
import com.cobre.notification.application.port.outbound.NotificationEventRepositoryPort;
import com.cobre.notification.domain.exception.DuplicateEventException;
import com.cobre.notification.domain.exception.UnauthorizedClientAccessException;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

@Service
public class CreateNotificationEventService implements CreateNotificationEventUseCase {

    private final NotificationEventRepositoryPort eventRepositoryPort;
    private final DeliverNotificationUseCase deliverNotificationUseCase;
    private final ManageWebhookSubscriptionUseCase manageWebhookSubscriptionUseCase;

    public CreateNotificationEventService(
            NotificationEventRepositoryPort eventRepositoryPort,
            DeliverNotificationUseCase deliverNotificationUseCase,
            ManageWebhookSubscriptionUseCase manageWebhookSubscriptionUseCase
    ) {
        this.eventRepositoryPort = eventRepositoryPort;
        this.deliverNotificationUseCase = deliverNotificationUseCase;
        this.manageWebhookSubscriptionUseCase = manageWebhookSubscriptionUseCase;
    }

    @Override
    public NotificationEvent createAndDispatch(CreateEventCommand command) {
        if (command == null || command.clientId() == null || command.clientId().isBlank()) {
            throw new UnauthorizedClientAccessException("Client identification (client_id) is required");
        }
        if (command.eventType() == null || command.eventType().isBlank()) {
            throw new IllegalArgumentException("event_type is required");
        }
        if (command.content() == null || command.content().isBlank()) {
            throw new IllegalArgumentException("content is required");
        }

        String clientId = command.clientId().trim();
        String eventId = command.eventId() != null && !command.eventId().isBlank()
                ? command.eventId().trim()
                : "EVT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Event identifiers are global: never let a client overwrite an existing event (own or foreign).
        if (eventRepositoryPort.findById(eventId).isPresent()) {
            throw new DuplicateEventException(eventId);
        }

        String webhookOverride = command.webhookUrl() != null && !command.webhookUrl().isBlank()
                ? command.webhookUrl().trim()
                : null;
        if (webhookOverride != null) {
            manageWebhookSubscriptionUseCase.updateWebhookUrl(clientId, webhookOverride);
        }

        NotificationEvent newEvent = new NotificationEvent(
                eventId,
                command.eventType().trim(),
                command.content().trim(),
                Instant.now(),
                DeliveryStatus.PENDING,
                clientId,
                webhookOverride,
                0,
                null,
                Collections.emptyList()
        );
        return deliverNotificationUseCase.deliverNotification(newEvent);
    }
}

