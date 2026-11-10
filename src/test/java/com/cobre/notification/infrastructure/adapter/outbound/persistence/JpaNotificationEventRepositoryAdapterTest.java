package com.cobre.notification.infrastructure.adapter.outbound.persistence;

import com.cobre.notification.application.port.outbound.NotificationEventRepositoryPort;
import com.cobre.notification.application.port.outbound.SubscriptionPort;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.model.WebhookSubscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "cobre.notification.webhook.allow-insecure-localhost=true",
        "cobre.notification.webhook.max-attempts=5",
        "cobre.notification.webhook.initial-backoff-ms=5"
})
@AutoConfigureMockMvc
@Transactional
class JpaNotificationEventRepositoryAdapterTest {

    @Autowired
    private NotificationEventRepositoryPort eventRepositoryPort;

    @Autowired
    private SubscriptionPort subscriptionPort;

    @Test
    @DisplayName("Should seed all 10 events from notification_events.json and 3 active client subscriptions on startup")
    void shouldSeedEventsAndSubscriptionsOnStartup() {
        assertThat(eventRepositoryPort.count()).isEqualTo(10L);

        Optional<WebhookSubscription> client1Sub = subscriptionPort.findActiveSubscriptionByClientId("CLIENT001");
        assertThat(client1Sub).isPresent();
        assertThat(client1Sub.get().shouldDeliverEvent("CLIENT001", "credit_card_payment")).isTrue();
        assertThat(client1Sub.get().shouldDeliverEvent("CLIENT002", "credit_card_payment")).isFalse();
    }

    @Test
    @DisplayName("Should isolate events by client_id and filter by delivery_status and date range")
    void shouldFilterEventsByClientStatusAndDateRange() {
        List<NotificationEvent> client1Events = eventRepositoryPort.findByCriteria(
                "CLIENT001", null, null, null
        );
        assertThat(client1Events)
                .hasSize(4)
                .extracting(NotificationEvent::getEventId)
                .containsExactly("EVT001", "EVT002", "EVT007", "EVT010");

        List<NotificationEvent> client2FailedEvents = eventRepositoryPort.findByCriteria(
                "CLIENT002", null, null, DeliveryStatus.FAILED
        );
        assertThat(client2FailedEvents)
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.getEventId()).isEqualTo("EVT003");
                    assertThat(event.getClientId()).isEqualTo("CLIENT002");
                    assertThat(event.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
                    assertThat(event.getDeliveryAttempts()).hasSize(1);
                });

        List<NotificationEvent> client1MorningEvents = eventRepositoryPort.findByCriteria(
                "CLIENT001",
                Instant.parse("2024-03-15T09:00:00Z"),
                Instant.parse("2024-03-15T11:00:00Z"),
                DeliveryStatus.COMPLETED
        );
        assertThat(client1MorningEvents)
                .hasSize(2)
                .extracting(NotificationEvent::getEventId)
                .containsExactly("EVT001", "EVT002");
    }

    @Test
    @DisplayName("Should enforce multi-tenant ownership when querying single event by ID and clientId")
    void shouldEnforceClientOwnershipOnSingleEventQuery() {
        assertThat(eventRepositoryPort.findByIdAndClientId("EVT001", "CLIENT001")).isPresent();
        assertThat(eventRepositoryPort.findByIdAndClientId("EVT001", "CLIENT002")).isEmpty();
    }

    @Test
    @DisplayName("Should persist new delivery attempt and update status when a failed event succeeds on replay")
    void shouldPersistReplayDeliveryAttempt() {
        NotificationEvent failedEvent = eventRepositoryPort.findByIdAndClientId("EVT003", "CLIENT002")
                .orElseThrow();
        assertThat(failedEvent.canBeReplayed()).isTrue();

        failedEvent.prepareForReplay();
        failedEvent.recordSuccessfulDelivery(200, 95L, Instant.parse("2024-03-16T10:00:00Z"));
        eventRepositoryPort.save(failedEvent);

        NotificationEvent updated = eventRepositoryPort.findByIdAndClientId("EVT003", "CLIENT002")
                .orElseThrow();
        assertThat(updated.getDeliveryStatus()).isEqualTo(DeliveryStatus.COMPLETED);
        assertThat(updated.getAttemptCount()).isEqualTo(2);
        assertThat(updated.getDeliveryAttempts()).hasSize(2);
        assertThat(updated.getDeliveryAttempts().get(1).success()).isTrue();
    }
}

