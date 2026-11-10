package com.cobre.notification.application.port.inbound;

/**
 * Inbound Port (Use Case) for changing the webhook endpoint of the authenticated client.
 */
public interface ManageWebhookSubscriptionUseCase {

    /**
     * Validates the URL (HTTPS + anti-SSRF) and only then persists it as the client's webhook target.
     */
    void updateWebhookUrl(String clientId, String webhookUrl);
}

