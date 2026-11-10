package com.cobre.notification.infrastructure.adapter.outbound.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "webhook_subscriptions")
public class WebhookSubscriptionJpaEntity {

    @Id
    @Column(name = "client_id", length = 64, nullable = false)
    private String clientId;

    @Column(name = "webhook_url", length = 512, nullable = false)
    private String webhookUrl;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "subscribed_event_types", length = 512, nullable = false)
    private String subscribedEventTypes;

    public WebhookSubscriptionJpaEntity() {
    }

    public WebhookSubscriptionJpaEntity(String clientId, String webhookUrl, boolean active, String subscribedEventTypes) {
        this.clientId = clientId;
        this.webhookUrl = webhookUrl;
        this.active = active;
        this.subscribedEventTypes = subscribedEventTypes;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getSubscribedEventTypes() {
        return subscribedEventTypes;
    }

    public void setSubscribedEventTypes(String subscribedEventTypes) {
        this.subscribedEventTypes = subscribedEventTypes;
    }
}

