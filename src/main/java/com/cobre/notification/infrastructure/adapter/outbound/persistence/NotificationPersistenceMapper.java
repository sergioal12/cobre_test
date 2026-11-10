package com.cobre.notification.infrastructure.adapter.outbound.persistence;

import com.cobre.notification.domain.model.DeadLetterNotification;
import com.cobre.notification.domain.model.DeliveryAttempt;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.model.WebhookSubscription;
import com.cobre.notification.infrastructure.adapter.outbound.persistence.entity.DeadLetterNotificationJpaEntity;
import com.cobre.notification.infrastructure.adapter.outbound.persistence.entity.DeliveryAttemptJpaEntity;
import com.cobre.notification.infrastructure.adapter.outbound.persistence.entity.NotificationEventJpaEntity;
import com.cobre.notification.infrastructure.adapter.outbound.persistence.entity.WebhookSubscriptionJpaEntity;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class NotificationPersistenceMapper {

    public NotificationEventJpaEntity toJpaEntity(NotificationEvent domain) {
        NotificationEventJpaEntity entity = new NotificationEventJpaEntity();
        entity.setEventId(domain.getEventId());
        entity.setEventType(domain.getEventType());
        entity.setContent(domain.getContent());
        entity.setDeliveryDate(domain.getDeliveryDate());
        entity.setDeliveryStatus(domain.getDeliveryStatus());
        entity.setClientId(domain.getClientId());
        entity.setWebhookUrl(domain.getWebhookUrl());
        entity.setAttemptCount(domain.getAttemptCount());
        entity.setLastErrorMessage(domain.getLastErrorMessage());

        for (DeliveryAttempt attempt : domain.getDeliveryAttempts()) {
            DeliveryAttemptJpaEntity attemptEntity = new DeliveryAttemptJpaEntity();
            attemptEntity.setAttemptNumber(attempt.attemptNumber());
            attemptEntity.setHttpStatusCode(attempt.httpStatusCode());
            attemptEntity.setSuccess(attempt.success());
            attemptEntity.setLatencyMs(attempt.latencyMs());
            attemptEntity.setErrorMessage(attempt.errorMessage());
            attemptEntity.setAttemptedAt(attempt.attemptedAt());
            entity.addDeliveryAttempt(attemptEntity);
        }

        return entity;
    }

    public NotificationEvent toDomainEvent(NotificationEventJpaEntity entity) {
        List<DeliveryAttempt> attempts = entity.getDeliveryAttempts() == null
                ? Collections.emptyList()
                : entity.getDeliveryAttempts().stream()
                        .map(a -> new DeliveryAttempt(
                                a.getAttemptNumber(),
                                a.getHttpStatusCode(),
                                a.isSuccess(),
                                a.getLatencyMs(),
                                a.getErrorMessage(),
                                a.getAttemptedAt()
                        ))
                        .toList();

        return new NotificationEvent(
                entity.getEventId(),
                entity.getEventType(),
                entity.getContent(),
                entity.getDeliveryDate(),
                entity.getDeliveryStatus(),
                entity.getClientId(),
                entity.getWebhookUrl(),
                entity.getAttemptCount(),
                entity.getLastErrorMessage(),
                attempts
        );
    }

    public WebhookSubscription toDomainSubscription(WebhookSubscriptionJpaEntity entity) {
        Set<String> types = entity.getSubscribedEventTypes() == null || entity.getSubscribedEventTypes().isBlank()
                ? Set.of("*")
                : Arrays.stream(entity.getSubscribedEventTypes().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());

        return new WebhookSubscription(
                entity.getClientId(),
                entity.getWebhookUrl(),
                entity.isActive(),
                types
        );
    }

    public DeadLetterNotification toDomainDlq(DeadLetterNotificationJpaEntity entity) {
        return new DeadLetterNotification(
                entity.getId(),
                entity.getEventId(),
                entity.getClientId(),
                entity.getEventType(),
                entity.getPayloadContent(),
                entity.getTargetWebhookUrl(),
                entity.getRetryAttemptsExhausted(),
                entity.getLastHttpStatusCode(),
                entity.getFailureReason(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getLastReprocessedAt()
        );
    }
}
