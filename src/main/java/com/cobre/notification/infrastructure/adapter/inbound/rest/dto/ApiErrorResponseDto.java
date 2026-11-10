package com.cobre.notification.infrastructure.adapter.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ApiErrorResponseDto(
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("status") int status,
        @JsonProperty("error") String error,
        @JsonProperty("message") String message,
        @JsonProperty("path") String path
) {
}

