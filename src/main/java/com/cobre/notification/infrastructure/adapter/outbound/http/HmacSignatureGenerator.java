package com.cobre.notification.infrastructure.adapter.outbound.http;

import com.cobre.notification.infrastructure.config.NotificationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Generates HMAC-SHA256 signatures for outgoing webhook payloads so clients can verify
 * authenticity and integrity (mitigates OWASP API8).
 */
@Component
public class HmacSignatureGenerator {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final NotificationProperties properties;

    public HmacSignatureGenerator(NotificationProperties properties) {
        this.properties = properties;
    }

    public String signPayload(String timestamp, String jsonPayload) {
        try {
            String secret = properties.getWebhook().getSigningSecret();
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            String dataToSign = timestamp + "." + jsonPayload;
            byte[] rawHmac = mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(rawHmac);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 webhook signature", ex);
        }
    }
}

