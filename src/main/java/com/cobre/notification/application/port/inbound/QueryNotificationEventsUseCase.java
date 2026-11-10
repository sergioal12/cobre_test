package com.cobre.notification.application.port.inbound;

import com.cobre.notification.domain.model.DeadLetterNotification;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Inbound Port (Use Case) for querying notification events via the Self-Service API.
 */
public interface QueryNotificationEventsUseCase {

    List<NotificationEvent> findEventsByCriteria(QueryCriteria criteria);

    NotificationEvent getEventById(String eventId, String clientId);

    default List<DeadLetterNotification> findDlqEventsByClient(String clientId) {
        return Collections.emptyList();
    }

    record QueryCriteria(
            String clientId,
            Instant fromDate,
            Instant toDate,
            DeliveryStatus deliveryStatus
    ) {
    }
}
