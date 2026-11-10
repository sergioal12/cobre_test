package com.cobre.notification.infrastructure.adapter.outbound.persistence.repository;

import com.cobre.notification.infrastructure.adapter.outbound.persistence.entity.DeadLetterNotificationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataDeadLetterQueueRepository
        extends JpaRepository<DeadLetterNotificationJpaEntity, Long> {

    Optional<DeadLetterNotificationJpaEntity> findByEventId(String eventId);

    List<DeadLetterNotificationJpaEntity> findByClientIdOrderByCreatedAtDesc(String clientId);
}

