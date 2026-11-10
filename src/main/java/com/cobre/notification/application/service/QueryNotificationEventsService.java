package com.cobre.notification.application.service;

import com.cobre.notification.application.port.inbound.QueryNotificationEventsUseCase;
import com.cobre.notification.application.port.outbound.DeadLetterQueuePort;
import com.cobre.notification.application.port.outbound.NotificationEventRepositoryPort;
import com.cobre.notification.domain.exception.EventNotFoundException;
import com.cobre.notification.domain.exception.UnauthorizedClientAccessException;
import com.cobre.notification.domain.model.DeadLetterNotification;
import com.cobre.notification.domain.model.NotificationEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QueryNotificationEventsService implements QueryNotificationEventsUseCase {

    private final NotificationEventRepositoryPort eventRepositoryPort;
    private final DeadLetterQueuePort deadLetterQueuePort;

    public QueryNotificationEventsService(
            NotificationEventRepositoryPort eventRepositoryPort,
            DeadLetterQueuePort deadLetterQueuePort
    ) {
        this.eventRepositoryPort = eventRepositoryPort;
        this.deadLetterQueuePort = deadLetterQueuePort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationEvent> findEventsByCriteria(QueryCriteria criteria) {
        if (criteria == null) {
            throw new IllegalArgumentException("QueryCriteria must not be null");
        }
        validateClientId(criteria.clientId());

        if (criteria.fromDate() != null && criteria.toDate() != null
                && criteria.fromDate().isAfter(criteria.toDate())) {
            throw new IllegalArgumentException("fromDate must be before or equal to toDate");
        }

        return eventRepositoryPort.findByCriteria(
                criteria.clientId().trim(),
                criteria.fromDate(),
                criteria.toDate(),
                criteria.deliveryStatus()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationEvent getEventById(String eventId, String clientId) {
        validateClientId(clientId);
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be null or blank");
        }

        return eventRepositoryPort.findByIdAndClientId(eventId.trim(), clientId.trim())
                .orElseThrow(() -> new EventNotFoundException(eventId.trim(), clientId.trim()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeadLetterNotification> findDlqEventsByClient(String clientId) {
        validateClientId(clientId);
        return deadLetterQueuePort.findByClientId(clientId.trim());
    }

    private void validateClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new UnauthorizedClientAccessException("Client identification (client_id) is required");
        }
    }
}
