package com.cobre.notification.infrastructure.adapter.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateNotificationEventRequestDto(
        @Size(max = 64, message = "event_id must not exceed 64 characters")
        @JsonProperty("event_id") String eventId,
        @NotBlank(message = "event_type is required")
        @Size(max = 100, message = "event_type must not exceed 100 characters")
        @JsonProperty("event_type") String eventType,
        @NotBlank(message = "content is required")
        @Size(max = 1000, message = "content must not exceed 1000 characters")
        @JsonProperty("content") String content,
        @Size(max = 64, message = "client_id must not exceed 64 characters")
        @JsonProperty("client_id") String clientId,
        @Size(max = 512, message = "webhook_url must not exceed 512 characters")
        @JsonProperty("webhook_url") String webhookUrl
) {
}
