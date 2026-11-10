package com.cobre.notification.infrastructure.adapter.outbound.persistence.repository;

import com.cobre.notification.infrastructure.adapter.outbound.persistence.entity.WebhookSubscriptionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataWebhookSubscriptionRepository
        extends JpaRepository<WebhookSubscriptionJpaEntity, String> {

    Optional<WebhookSubscriptionJpaEntity> findByClientIdAndActiveTrue(String clientId);
}

