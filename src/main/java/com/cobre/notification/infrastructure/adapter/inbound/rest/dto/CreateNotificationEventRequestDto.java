package com.cobre.notification.infrastructure.adapter.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CreateNotificationEventRequestDto(
        @JsonProperty("event_id") String eventId,
        @NotBlank(message = "event_type is required")
        @JsonProperty("event_type") String eventType,
        @NotBlank(message = "content is required")
        @JsonProperty("content") String content,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("webhook_url") String webhookUrl
) {
}

