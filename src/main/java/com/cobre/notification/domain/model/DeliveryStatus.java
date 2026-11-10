package com.cobre.notification.domain.model;

import java.util.Arrays;

/**
 * Represents the delivery status of a notification event in the domain.
 * Compatible with the lowercase values used in notification_events.json ("completed", "failed").
 */
public enum DeliveryStatus {
    PENDING("pending"),
    RETRYING("retrying"),
    COMPLETED("completed"),
    FAILED("failed");

    private final String value;

    DeliveryStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static DeliveryStatus fromValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("delivery_status cannot be null or blank");
        }
        return Arrays.stream(values())
                .filter(status -> status.value.equalsIgnoreCase(rawValue.trim())
                        || status.name().equalsIgnoreCase(rawValue.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown delivery_status: " + rawValue));
    }
}

