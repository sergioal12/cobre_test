package com.cobre.notification.infrastructure.adapter.outbound.http;

import com.cobre.notification.infrastructure.config.NotificationProperties;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;

/**
 * Mitigates OWASP A10 / API7 (Server-Side Request Forgery - SSRF) by validating that webhook URLs
 * use HTTPS and do not resolve to loopback, link-local (cloud metadata 169.254.169.254), or private IPs
 * unless explicitly allowed for local testing.
 */
@Component
public class WebhookUrlSecurityValidator {

    private final NotificationProperties properties;

    public WebhookUrlSecurityValidator(NotificationProperties properties) {
        this.properties = properties;
    }

    public void validateTargetUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Webhook URL must not be blank");
        }

        URI uri = URI.create(rawUrl.trim());
        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (scheme == null || host == null) {
            throw new IllegalArgumentException("Invalid webhook URL: " + rawUrl);
        }

        boolean allowInsecureLocal = properties.getWebhook().isAllowInsecureLocalhost();
        if (!"https".equalsIgnoreCase(scheme) && !(allowInsecureLocal && "http".equalsIgnoreCase(scheme))) {
            throw new SecurityException("Webhook delivery requires HTTPS protocol: " + rawUrl);
        }

        if (!allowInsecureLocal) {
            try {
                InetAddress[] addresses = InetAddress.getAllByName(host);
                for (InetAddress address : addresses) {
                    if (address.isLoopbackAddress()
                            || address.isLinkLocalAddress()
                            || address.isSiteLocalAddress()
                            || address.isAnyLocalAddress()
                            || address.isMulticastAddress()) {
                        throw new SecurityException(
                                "SSRF protection blocked webhook target resolving to private/internal address: " + host
                        );
                    }
                }
            } catch (SecurityException se) {
                throw se;
            } catch (Exception ex) {
                throw new IllegalArgumentException("Unable to resolve webhook host '" + host + "': " + ex.getMessage(), ex);
            }
        }
    }
}

