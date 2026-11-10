package com.cobre.notification.application.service;

import com.cobre.notification.application.port.inbound.QueryNotificationEventsUseCase;
import com.cobre.notification.application.port.inbound.QueryNotificationEventsUseCase.QueryCriteria;
import com.cobre.notification.application.port.inbound.ReplayNotificationUseCase;
import com.cobre.notification.application.port.outbound.DeadLetterQueuePort;
import com.cobre.notification.domain.exception.EventNotFoundException;
import com.cobre.notification.domain.exception.InvalidReplayStateException;
import com.cobre.notification.domain.model.DeadLetterNotification;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.DlqStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.infrastructure.adapter.outbound.http.WebClientWebhookDeliveryAdapter;
import com.cobre.notification.infrastructure.adapter.outbound.http.WebhookUrlSecurityValidator;
import com.cobre.notification.infrastructure.adapter.outbound.persistence.entity.WebhookSubscriptionJpaEntity;
import com.cobre.notification.infrastructure.adapter.outbound.persistence.repository.SpringDataWebhookSubscriptionRepository;
import com.cobre.notification.infrastructure.config.NotificationProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "cobre.notification.webhook.allow-insecure-localhost=true",
        "cobre.notification.webhook.max-attempts=5",
        "cobre.notification.webhook.initial-backoff-ms=5"
})
@AutoConfigureMockMvc
@Transactional
class NotificationApplicationServicesTest {

    @Autowired
    private QueryNotificationEventsUseCase queryUseCase;

    @Autowired
    private ReplayNotificationUseCase replayUseCase;

    @Autowired
    private DeadLetterQueuePort deadLetterQueuePort;

    @Autowired
    private SpringDataWebhookSubscriptionRepository subscriptionRepository;

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start(InetAddress.getByName("127.0.0.1"), 0);
        String mockWebhookUrl = "http://127.0.0.1:" + mockWebServer.getPort() + "/webhook/client";

        subscriptionRepository.save(new WebhookSubscriptionJpaEntity(
                "CLIENT002",
                mockWebhookUrl,
                true,
                "*"
        ));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @Test
    @DisplayName("Should query events for a client and block access to events belonging to another client (BOLA protection)")
    void shouldQueryEventsAndEnforceMultiTenantIsolation() {
        List<NotificationEvent> client2Events = queryUseCase.findEventsByCriteria(
                new QueryCriteria("CLIENT002", null, null, null)
        );
        assertThat(client2Events).hasSize(3);

        NotificationEvent evt003 = queryUseCase.getEventById("EVT003", "CLIENT002");
        assertThat(evt003.getEventType()).isEqualTo("credit_transfer");

        assertThatThrownBy(() -> queryUseCase.getEventById("EVT003", "CLIENT001"))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    @DisplayName("Should replay a failed event, retry on transient 503 failure, succeed on 200 OK, and mark DLQ entry as REPROCESSED")
    void shouldReplayFailedEventWithRetryAndMarkDlqAsReprocessed() throws Exception {
        // First webhook attempt fails with 503, second attempt succeeds with 200 OK
        mockWebServer.enqueue(new MockResponse().setResponseCode(503).setBody("Service Unavailable"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"received\":true}"));

        NotificationEvent replayed = replayUseCase.replayFailedNotification("EVT003", "CLIENT002", null);

        assertThat(replayed.getDeliveryStatus()).isEqualTo(DeliveryStatus.COMPLETED);
        // 1 initial seeded attempt + 1 failed retry (503) + 1 successful retry (200) = 3 attempts
        assertThat(replayed.getAttemptCount()).isEqualTo(3);
        assertThat(replayed.getDeliveryAttempts()).hasSize(3);

        RecordedRequest firstAttempt = mockWebServer.takeRequest(2, TimeUnit.SECONDS);
        RecordedRequest secondAttempt = mockWebServer.takeRequest(2, TimeUnit.SECONDS);
        assertThat(firstAttempt).isNotNull();
        assertThat(secondAttempt).isNotNull();
        assertThat(secondAttempt.getHeader(WebClientWebhookDeliveryAdapter.HEADER_EVENT_ID)).isEqualTo("EVT003");
        assertThat(secondAttempt.getHeader(WebClientWebhookDeliveryAdapter.HEADER_CLIENT_ID)).isEqualTo("CLIENT002");
        assertThat(secondAttempt.getHeader(WebClientWebhookDeliveryAdapter.HEADER_SIGNATURE)).startsWith("sha256=");

        Optional<DeadLetterNotification> dlqEntry = deadLetterQueuePort.findByEventId("EVT003");
        assertThat(dlqEntry).isPresent();
        assertThat(dlqEntry.get().status()).isEqualTo(DlqStatus.REPROCESSED);
        assertThat(dlqEntry.get().lastReprocessedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should execute maximum 5 retry attempts and persist event in notification_dlq table when all 5 retries fail")
    void shouldExhaustFiveRetriesAndSaveInDlqTableForManualReprocessing() {
        for (int i = 0; i < 5; i++) {
            mockWebServer.enqueue(new MockResponse().setResponseCode(502).setBody("Bad Gateway"));
        }

        NotificationEvent failedAfterFiveRetries = replayUseCase.replayFailedNotification("EVT003", "CLIENT002", null);

        assertThat(failedAfterFiveRetries.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        // 1 initial seeded attempt + 5 retry attempts = 6 total attempts recorded
        assertThat(failedAfterFiveRetries.getAttemptCount()).isEqualTo(6);
        assertThat(mockWebServer.getRequestCount()).isEqualTo(5);

        Optional<DeadLetterNotification> dlqEntryOpt = deadLetterQueuePort.findByEventId("EVT003");
        assertThat(dlqEntryOpt).isPresent();
        DeadLetterNotification dlqEntry = dlqEntryOpt.get();
        assertThat(dlqEntry.clientId()).isEqualTo("CLIENT002");
        assertThat(dlqEntry.eventType()).isEqualTo("credit_transfer");
        assertThat(dlqEntry.payloadContent()).contains("Bank transfer received from Account #4567");
        assertThat(dlqEntry.retryAttemptsExhausted()).isEqualTo(5);
        assertThat(dlqEntry.lastHttpStatusCode()).isEqualTo(502);
        assertThat(dlqEntry.status()).isEqualTo(DlqStatus.PENDING_MANUAL_REPROCESS);
        assertThat(dlqEntry.failureReason()).contains("Exhausted 5 retry attempts");
    }

    @Test
    @DisplayName("Should reject replay when event is already in COMPLETED state")
    void shouldRejectReplayWhenEventIsAlreadyCompleted() {
        assertThatThrownBy(() -> replayUseCase.replayFailedNotification("EVT004", "CLIENT002", null))
                .isInstanceOf(InvalidReplayStateException.class)
                .hasMessageContaining("EVT004");
    }

    @Test
    @DisplayName("SSRF validator should block non-HTTPS and private/internal IP targets when allowInsecureLocalhost is false")
    void shouldBlockSsrfTargetsWhenStrictModeEnabled() {
        NotificationProperties strictProps = new NotificationProperties();
        strictProps.getWebhook().setAllowInsecureLocalhost(false);
        WebhookUrlSecurityValidator strictValidator = new WebhookUrlSecurityValidator(strictProps);

        assertThatThrownBy(() -> strictValidator.validateTargetUrl("http://example.com/webhook"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("HTTPS");

        assertThatThrownBy(() -> strictValidator.validateTargetUrl("https://127.0.0.1/admin"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SSRF protection blocked");

        assertThatThrownBy(() -> strictValidator.validateTargetUrl("https://169.254.169.254/latest/meta-data"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SSRF protection blocked");
    }
}
