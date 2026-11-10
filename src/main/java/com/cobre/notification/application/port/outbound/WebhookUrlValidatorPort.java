package com.cobre.notification.application.port.outbound;

/**
 * Outbound Port that validates a client-provided webhook URL (HTTPS, anti-SSRF) before it is persisted
 * or used as a delivery target.
 */
public interface WebhookUrlValidatorPort {

    /**
     * @throws IllegalArgumentException if the URL is malformed, too long or cannot be resolved
     * @throws SecurityException        if the URL targets a non-HTTPS or private/internal destination
     */
    void validate(String webhookUrl);
}

