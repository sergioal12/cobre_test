package com.cobre.notification.infrastructure.adapter.outbound.persistence;

import com.cobre.notification.application.port.outbound.DeadLetterQueuePort;
import com.cobre.notification.application.port.outbound.NotificationEventRepositoryPort;
import com.cobre.notification.domain.model.DeadLetterNotification;
import com.cobre.notification.domain.model.DeliveryAttempt;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.DlqStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.infrastructure.adapter.outbound.persistence.entity.WebhookSubscriptionJpaEntity;
import com.cobre.notification.infrastructure.adapter.outbound.persistence.repository.SpringDataWebhookSubscriptionRepository;
import com.cobre.notification.infrastructure.config.NotificationProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seeds initial client subscriptions, notification events, and DLQ records from notification_events.json on startup.
 */
@Component
public class NotificationEventsDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventsDataSeeder.class);

    private final NotificationEventRepositoryPort eventRepositoryPort;
    private final DeadLetterQueuePort deadLetterQueuePort;
    private final SpringDataWebhookSubscriptionRepository subscriptionRepository;
    private final NotificationProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public NotificationEventsDataSeeder(
            NotificationEventRepositoryPort eventRepositoryPort,
            DeadLetterQueuePort deadLetterQueuePort,
            SpringDataWebhookSubscriptionRepository subscriptionRepository,
            NotificationProperties properties,
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper
    ) {
        this.eventRepositoryPort = eventRepositoryPort;
        this.deadLetterQueuePort = deadLetterQueuePort;
        this.subscriptionRepository = subscriptionRepository;
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        Resource resource = resolveSeedResource();
        if (resource == null || !resource.exists()) {
            log.warn("No notification_events.json seed file found at '{}' or '{}'",
                    properties.getSeedFile(), properties.getClasspathSeedFile());
            return;
        }

        try (InputStream inputStream = resource.getInputStream()) {
            SeedPayload payload = objectMapper.readValue(inputStream, SeedPayload.class);
            if (payload == null || payload.events() == null || payload.events().isEmpty()) {
                log.warn("Seed file '{}' contains no events", resource.getDescription());
                return;
            }

            String defaultWebhookUrl = properties.getWebhook().getDefaultUrl();
            seedSubscriptions(payload.events(), defaultWebhookUrl);

            List<NotificationEvent> domainEvents = payload.events().stream()
                    .map(item -> toDomainEvent(item, defaultWebhookUrl))
                    .toList();

            if (eventRepositoryPort.count() == 0) {
                eventRepositoryPort.saveAll(domainEvents);
                log.info("Successfully seeded {} notification events and client subscriptions from {}",
                        domainEvents.size(), resource.getDescription());
            } else {
                log.info("Database already contains {} notification events; ensuring DLQ entries for failed events.",
                        eventRepositoryPort.count());
            }

            seedDlqForFailedEvents(domainEvents);
        }
    }

    private void seedSubscriptions(List<SeedEventItem> items, String defaultWebhookUrl) {
        Set<String> clientIds = items.stream()
                .map(SeedEventItem::clientId)
                .collect(Collectors.toSet());

        for (String clientId : clientIds) {
            WebhookSubscriptionJpaEntity existing = subscriptionRepository.findById(clientId).orElse(null);
            if (existing == null) {
                subscriptionRepository.save(new WebhookSubscriptionJpaEntity(
                        clientId,
                        defaultWebhookUrl,
                        true,
                        "*"
                ));
            } else if (!defaultWebhookUrl.equals(existing.getWebhookUrl())) {
                existing.setWebhookUrl(defaultWebhookUrl);
                subscriptionRepository.save(existing);
            }
        }
    }

    /**
     * Reconciles the DLQ with the PERSISTED state of each event (never with the seed file, which only reflects the
     * initial snapshot): failed events lacking a DLQ row get one, and rows of events that were since completed
     * (e.g. through a manual replay) are closed as REPROCESSED instead of being resurrected as pending.
     */
    private void seedDlqForFailedEvents(List<NotificationEvent> seededEvents) {
        for (NotificationEvent seeded : seededEvents) {
            NotificationEvent persisted = eventRepositoryPort.findById(seeded.getEventId()).orElse(null);
            if (persisted == null) {
                continue;
            }

            var existingDlq = deadLetterQueuePort.findByEventId(persisted.getEventId());

            if (persisted.getDeliveryStatus() == DeliveryStatus.FAILED && existingDlq.isEmpty()) {
                deadLetterQueuePort.saveForManualReprocessing(new DeadLetterNotification(
                        null,
                        persisted.getEventId(),
                        persisted.getClientId(),
                        persisted.getEventType(),
                        persisted.getContent(),
                        persisted.getWebhookUrl(),
                        NotificationEvent.DEFAULT_MAX_RETRY_ATTEMPTS,
                        503,
                        "Exhausted 5 retry attempts. Initial delivery failed before self-service replay",
                        DlqStatus.PENDING_MANUAL_REPROCESS,
                        persisted.getDeliveryDate(),
                        null
                ));
            } else if (persisted.getDeliveryStatus() == DeliveryStatus.COMPLETED
                    && existingDlq.isPresent()
                    && existingDlq.get().status() == DlqStatus.PENDING_MANUAL_REPROCESS) {
                deadLetterQueuePort.markAsReprocessedIfPresent(persisted.getEventId(), persisted.getDeliveryDate());
            }
        }
    }

    private NotificationEvent toDomainEvent(SeedEventItem item, String defaultWebhookUrl) {
        Instant deliveryDate = Instant.parse(item.deliveryDate());
        DeliveryStatus status = DeliveryStatus.fromValue(item.deliveryStatus());
        boolean isCompleted = status == DeliveryStatus.COMPLETED;
        int initialHttpStatus = isCompleted ? 200 : 503;
        String initialError = isCompleted ? null : "Initial delivery failed before self-service replay";

        DeliveryAttempt initialAttempt = new DeliveryAttempt(
                1,
                initialHttpStatus,
                isCompleted,
                isCompleted ? 120L : 5000L,
                initialError,
                deliveryDate
        );

        return new NotificationEvent(
                item.eventId(),
                item.eventType(),
                item.content(),
                deliveryDate,
                status,
                item.clientId(),
                defaultWebhookUrl,
                1,
                initialError,
                List.of(initialAttempt)
        );
    }

    private Resource resolveSeedResource() {
        Resource primary = resourceLoader.getResource(properties.getSeedFile());
        if (primary.exists()) {
            return primary;
        }
        return resourceLoader.getResource(properties.getClasspathSeedFile());
    }

    public record SeedPayload(
            @JsonProperty("events") List<SeedEventItem> events
    ) {
    }

    public record SeedEventItem(
            @JsonProperty("event_id") String eventId,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("content") String content,
            @JsonProperty("delivery_date") String deliveryDate,
            @JsonProperty("delivery_status") String deliveryStatus,
            @JsonProperty("client_id") String clientId
    ) {
    }
}
