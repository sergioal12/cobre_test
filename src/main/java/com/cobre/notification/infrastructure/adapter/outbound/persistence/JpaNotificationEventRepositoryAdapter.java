package com.cobre.notification.infrastructure.adapter.outbound.persistence;

import com.cobre.notification.application.port.outbound.NotificationEventRepositoryPort;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.infrastructure.adapter.outbound.persistence.entity.NotificationEventJpaEntity;
import com.cobre.notification.infrastructure.adapter.outbound.persistence.repository.SpringDataNotificationEventRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class JpaNotificationEventRepositoryAdapter implements NotificationEventRepositoryPort {

    private final SpringDataNotificationEventRepository repository;
    private final NotificationPersistenceMapper mapper;

    public JpaNotificationEventRepositoryAdapter(
            SpringDataNotificationEventRepository repository,
            NotificationPersistenceMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public NotificationEvent save(NotificationEvent event) {
        NotificationEventJpaEntity saved = repository.save(mapper.toJpaEntity(event));
        return mapper.toDomainEvent(saved);
    }

    @Override
    @Transactional
    public void saveAll(List<NotificationEvent> events) {
        List<NotificationEventJpaEntity> entities = events.stream()
                .map(mapper::toJpaEntity)
                .toList();
        repository.saveAll(entities);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationEvent> findByIdAndClientId(String eventId, String clientId) {
        return repository.findByEventIdAndClientId(eventId, clientId)
                .map(mapper::toDomainEvent);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationEvent> findById(String eventId) {
        return repository.findById(eventId)
                .map(mapper::toDomainEvent);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationEvent> findByCriteria(
            String clientId,
            Instant fromDate,
            Instant toDate,
            DeliveryStatus deliveryStatus
    ) {
        Specification<NotificationEventJpaEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Mandatory multi-tenant isolation by clientId
            predicates.add(cb.equal(root.get("clientId"), clientId));

            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("deliveryDate"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("deliveryDate"), toDate));
            }
            if (deliveryStatus != null) {
                predicates.add(cb.equal(root.get("deliveryStatus"), deliveryStatus));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return repository.findAll(spec, Sort.by(Sort.Direction.ASC, "deliveryDate", "eventId"))
                .stream()
                .map(mapper::toDomainEvent)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return repository.count();
    }
}

