package com.cobre.notification.infrastructure.adapter.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record NotificationEventResponseDto(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("content") String content,
        @JsonProperty("delivery_date") Instant deliveryDate,
        @JsonProperty("delivery_status") String deliveryStatus,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("attempt_count") int attemptCount,
        @JsonProperty("webhook_url") String webhookUrl
) {
}

