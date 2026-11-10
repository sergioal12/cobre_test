package com.cobre.notification.application.service;

import com.cobre.notification.application.port.inbound.ManageWebhookSubscriptionUseCase;
import com.cobre.notification.application.port.outbound.SubscriptionPort;
import com.cobre.notification.application.port.outbound.WebhookUrlValidatorPort;
import org.springframework.stereotype.Service;

@Service
public class ManageWebhookSubscriptionService implements ManageWebhookSubscriptionUseCase {

    private final SubscriptionPort subscriptionPort;
    private final WebhookUrlValidatorPort webhookUrlValidator;

    public ManageWebhookSubscriptionService(
            SubscriptionPort subscriptionPort,
            WebhookUrlValidatorPort webhookUrlValidator
    ) {
        this.subscriptionPort = subscriptionPort;
        this.webhookUrlValidator = webhookUrlValidator;
    }

    @Override
    public void updateWebhookUrl(String clientId, String webhookUrl) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be null or blank");
        }
        // Validate BEFORE persisting: an unsafe URL must never be stored as a delivery target.
        webhookUrlValidator.validate(webhookUrl);
        subscriptionPort.updateWebhookUrl(clientId.trim(), webhookUrl.trim());
    }
}

