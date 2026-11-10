package com.cobre.notification.infrastructure.adapter.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeadLetterNotificationResponseDto(
        @JsonProperty("dlq_id") Long id,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("payload_content") String payloadContent,
        @JsonProperty("target_webhook_url") String targetWebhookUrl,
        @JsonProperty("retry_attempts_exhausted") int retryAttemptsExhausted,
        @JsonProperty("last_http_status_code") Integer lastHttpStatusCode,
        @JsonProperty("failure_reason") String failureReason,
        @JsonProperty("reprocess_status") String status,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("last_reprocessed_at") Instant lastReprocessedAt
) {
}

