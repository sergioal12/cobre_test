package com.cobre.notification.application.service;

import com.cobre.notification.application.port.outbound.SubscriptionPort;
import com.cobre.notification.domain.model.WebhookSubscription;
import com.cobre.notification.infrastructure.adapter.outbound.http.WebhookUrlSecurityValidator;
import com.cobre.notification.infrastructure.config.NotificationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Strict-mode (production defaults) tests: an unsafe webhook URL must be rejected BEFORE it is persisted.
 */
class ManageWebhookSubscriptionServiceTest {

    private final List<String> persistedUpdates = new ArrayList<>();

    private final SubscriptionPort recordingPort = new SubscriptionPort() {
        @Override
        public Optional<WebhookSubscription> findActiveSubscriptionByClientId(String clientId) {
            return Optional.empty();
        }

        @Override
        public void updateWebhookUrl(String clientId, String newWebhookUrl) {
            persistedUpdates.add(clientId + "=" + newWebhookUrl);
        }
    };

    private final ManageWebhookSubscriptionService service = new ManageWebhookSubscriptionService(
            recordingPort,
            new WebhookUrlSecurityValidator(new NotificationProperties())
    );

    @Test
    @DisplayName("Non-HTTPS, loopback, link-local and private targets must be rejected and never persisted")
    void shouldRejectUnsafeUrlsWithoutPersisting() {
        assertThatThrownBy(() -> service.updateWebhookUrl("CLIENT001", "http://example.com/hook"))
                .isInstanceOf(SecurityException.class).hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> service.updateWebhookUrl("CLIENT001", "https://169.254.169.254/latest/meta-data"))
                .isInstanceOf(SecurityException.class).hasMessageContaining("SSRF");
        assertThatThrownBy(() -> service.updateWebhookUrl("CLIENT001", "https://127.0.0.1:8080/actuator"))
                .isInstanceOf(SecurityException.class).hasMessageContaining("SSRF");
        assertThatThrownBy(() -> service.updateWebhookUrl("CLIENT001", "https://10.0.0.5/internal"))
                .isInstanceOf(SecurityException.class).hasMessageContaining("SSRF");
        assertThatThrownBy(() -> service.updateWebhookUrl("CLIENT001", "https://192.168.1.10/internal"))
                .isInstanceOf(SecurityException.class).hasMessageContaining("SSRF");

        assertThat(persistedUpdates).isEmpty();
    }

    @Test
    @DisplayName("Blank and oversized URLs must be rejected and never persisted")
    void shouldRejectBlankAndOversizedUrls() {
        assertThatThrownBy(() -> service.updateWebhookUrl("CLIENT001", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateWebhookUrl("CLIENT001", "https://8.8.8.8/" + "a".repeat(600)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("512");

        assertThat(persistedUpdates).isEmpty();
    }

    @Test
    @DisplayName("A public HTTPS URL should be validated and then persisted for the client")
    void shouldPersistSafeUrl() {
        service.updateWebhookUrl(" CLIENT001 ", " https://8.8.8.8/hook ");

        assertThat(persistedUpdates).containsExactly("CLIENT001=https://8.8.8.8/hook");
    }
}

