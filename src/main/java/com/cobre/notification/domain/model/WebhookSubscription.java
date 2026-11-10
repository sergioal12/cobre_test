package com.cobre.notification.domain.model;

import java.util.Set;

/**
 * Represents a client's webhook subscription configuration.
 * Ensures that notifications sent to every client belong to events subscribed by that client.
 */
public record WebhookSubscription(
        String clientId,
        String webhookUrl,
        boolean active,
        Set<String> subscribedEventTypes
) {
    public boolean shouldDeliverEvent(String eventClientId, String eventType) {
        if (!active || webhookUrl == null || webhookUrl.isBlank()) {
            return false;
        }
        if (!this.clientId.equals(eventClientId)) {
            return false;
        }
        return subscribedEventTypes == null
                || subscribedEventTypes.isEmpty()
                || subscribedEventTypes.contains("*")
                || subscribedEventTypes.contains(eventType);
    }
}

