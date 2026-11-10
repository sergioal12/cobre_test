package com.cobre.notification.infrastructure.adapter.outbound.persistence;

import com.cobre.notification.application.port.outbound.DeadLetterQueuePort;
import com.cobre.notification.domain.model.DeadLetterNotification;
import com.cobre.notification.domain.model.DlqStatus;
import com.cobre.notification.infrastructure.adapter.outbound.persistence.entity.DeadLetterNotificationJpaEntity;
import com.cobre.notification.infrastructure.adapter.outbound.persistence.repository.SpringDataDeadLetterQueueRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class JpaDeadLetterQueueAdapter implements DeadLetterQueuePort {

    private final SpringDataDeadLetterQueueRepository repository;
    private final NotificationPersistenceMapper mapper;

    public JpaDeadLetterQueueAdapter(
            SpringDataDeadLetterQueueRepository repository,
            NotificationPersistenceMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public DeadLetterNotification saveForManualReprocessing(DeadLetterNotification dlq) {
        DeadLetterNotificationJpaEntity entity = repository.findByEventId(dlq.eventId())
                .orElseGet(DeadLetterNotificationJpaEntity::new);

        entity.setEventId(dlq.eventId());
        entity.setClientId(dlq.clientId());
        entity.setEventType(dlq.eventType());
        entity.setPayloadContent(dlq.payloadContent());
        entity.setTargetWebhookUrl(dlq.targetWebhookUrl());
        entity.setRetryAttemptsExhausted(dlq.retryAttemptsExhausted());
        entity.setLastHttpStatusCode(dlq.lastHttpStatusCode());
        entity.setFailureReason(dlq.failureReason());
        entity.setStatus(dlq.status());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(dlq.createdAt());
        } else {
            entity.setLastReprocessedAt(Instant.now());
        }

        DeadLetterNotificationJpaEntity saved = repository.save(entity);
        return mapper.toDomainDlq(saved);
    }

    @Override
    @Transactional
    public void markAsReprocessedIfPresent(String eventId, Instant reprocessedAt) {
        repository.findByEventId(eventId).ifPresent(entity -> {
            entity.setStatus(DlqStatus.REPROCESSED);
            entity.setLastReprocessedAt(reprocessedAt != null ? reprocessedAt : Instant.now());
            repository.save(entity);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeadLetterNotification> findByEventId(String eventId) {
        return repository.findByEventId(eventId)
                .map(mapper::toDomainDlq);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeadLetterNotification> findByClientId(String clientId) {
        return repository.findByClientIdOrderByCreatedAtDesc(clientId)
                .stream()
                .map(mapper::toDomainDlq)
                .toList();
    }
}

