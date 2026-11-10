package com.cobre.notification.application.port.outbound;

import com.cobre.notification.domain.model.WebhookSubscription;

import java.util.Optional;

/**
 * Outbound Port for verifying client webhook subscriptions and resolving target URLs.
 */
public interface SubscriptionPort {

    Optional<WebhookSubscription> findActiveSubscriptionByClientId(String clientId);

    void updateWebhookUrl(String clientId, String newWebhookUrl);
}
