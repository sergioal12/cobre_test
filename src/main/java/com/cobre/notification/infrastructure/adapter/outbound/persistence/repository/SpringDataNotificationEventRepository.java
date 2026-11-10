package com.cobre.notification.infrastructure.adapter.outbound.persistence.repository;

import com.cobre.notification.infrastructure.adapter.outbound.persistence.entity.NotificationEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataNotificationEventRepository extends
        JpaRepository<NotificationEventJpaEntity, String>,
        JpaSpecificationExecutor<NotificationEventJpaEntity> {

    Optional<NotificationEventJpaEntity> findByEventIdAndClientId(String eventId, String clientId);
}

