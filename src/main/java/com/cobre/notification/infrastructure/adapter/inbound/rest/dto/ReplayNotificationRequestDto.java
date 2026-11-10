package com.cobre.notification.infrastructure.adapter.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReplayNotificationRequestDto(
        @JsonProperty("webhook_url") String webhookUrl
) {
}

