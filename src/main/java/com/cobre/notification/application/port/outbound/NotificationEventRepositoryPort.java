package com.cobre.notification.application.port.outbound;

import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound Port for persisting and querying NotificationEvents.
 * Pure Java interface (zero Spring Data dependencies).
 */
public interface NotificationEventRepositoryPort {

    NotificationEvent save(NotificationEvent event);

    void saveAll(List<NotificationEvent> events);

    Optional<NotificationEvent> findByIdAndClientId(String eventId, String clientId);

    Optional<NotificationEvent> findById(String eventId);

    List<NotificationEvent> findByCriteria(
            String clientId,
            Instant fromDate,
            Instant toDate,
            DeliveryStatus deliveryStatus
    );

    long count();
}

