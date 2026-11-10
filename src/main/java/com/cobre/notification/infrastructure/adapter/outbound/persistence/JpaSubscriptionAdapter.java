package com.cobre.notification.infrastructure.adapter.outbound.persistence;

import com.cobre.notification.application.port.outbound.SubscriptionPort;
import com.cobre.notification.domain.model.WebhookSubscription;
import com.cobre.notification.infrastructure.adapter.outbound.persistence.entity.WebhookSubscriptionJpaEntity;
import com.cobre.notification.infrastructure.adapter.outbound.persistence.repository.SpringDataWebhookSubscriptionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class JpaSubscriptionAdapter implements SubscriptionPort {

    private final SpringDataWebhookSubscriptionRepository repository;
    private final NotificationPersistenceMapper mapper;

    public JpaSubscriptionAdapter(
            SpringDataWebhookSubscriptionRepository repository,
            NotificationPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WebhookSubscription> findActiveSubscriptionByClientId(String clientId) {
        return repository.findByClientIdAndActiveTrue(clientId)
                .map(mapper::toDomainSubscription);
    }

    @Override
    @Transactional
    public void updateWebhookUrl(String clientId, String newWebhookUrl) {
        WebhookSubscriptionJpaEntity entity = repository.findById(clientId)
                .orElseGet(() -> new WebhookSubscriptionJpaEntity(clientId, newWebhookUrl, true, "*"));
        entity.setWebhookUrl(newWebhookUrl);
        entity.setActive(true);
        repository.save(entity);
    }
}
