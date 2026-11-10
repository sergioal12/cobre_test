package com.cobre.notification.infrastructure.security;

import com.cobre.notification.domain.exception.UnauthorizedClientAccessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RFC 7519 compliant HMAC-SHA256 (HS256) JWT generator and validator for multi-tenant client authentication.
 */
@Component
public class JwtTokenService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String ISSUER = "cobre-notification-service";

    private final ObjectMapper objectMapper;
    private final String jwtSecret;
    private final long expirationSeconds;

    public JwtTokenService(
            ObjectMapper objectMapper,
            @Value("${cobre.security.jwt.secret:cobre-jwt-signing-secret-key-2026-hs256}") String jwtSecret,
            @Value("${cobre.security.jwt.expiration-seconds:3600}") long expirationSeconds
    ) {
        this.objectMapper = objectMapper;
        this.jwtSecret = jwtSecret;
        this.expirationSeconds = expirationSeconds > 0 ? expirationSeconds : 3600L;
    }

    public GeneratedToken generateToken(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("client_id is required to generate a JWT token");
        }
        String normalizedClient = clientId.trim();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);

        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", normalizedClient);
            payload.put("client_id", normalizedClient);
            payload.put("iss", ISSUER);
            payload.put("iat", issuedAt.getEpochSecond());
            payload.put("exp", expiresAt.getEpochSecond());

            String encodedHeader = base64UrlEncode(objectMapper.writeValueAsBytes(header));
            String encodedPayload = base64UrlEncode(objectMapper.writeValueAsBytes(payload));
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = signHmacSha256(signingInput);

            String token = signingInput + "." + signature;
            return new GeneratedToken(token, "Bearer", expirationSeconds, normalizedClient, issuedAt, expiresAt);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate JWT token", ex);
        }
    }

    public ValidatedToken validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedClientAccessException("JWT token is empty");
        }

        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            throw new UnauthorizedClientAccessException("Invalid JWT format: expected 3 parts (header.payload.signature)");
        }

        String signingInput = parts[0] + "." + parts[1];
        String expectedSignature = signHmacSha256(signingInput);

        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8)
        )) {
            throw new UnauthorizedClientAccessException("Invalid JWT signature");
        }

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = objectMapper.readValue(payloadBytes, new TypeReference<>() {});

            Number expNumber = (Number) claims.get("exp");
            Number iatNumber = (Number) claims.get("iat");
            if (expNumber == null) {
                throw new UnauthorizedClientAccessException("Invalid JWT token: missing 'exp' claim");
            }

            Instant expiresAt = Instant.ofEpochSecond(expNumber.longValue());
            Instant issuedAt = iatNumber != null ? Instant.ofEpochSecond(iatNumber.longValue()) : null;

            if (Instant.now().isAfter(expiresAt)) {
                throw new UnauthorizedClientAccessException("JWT token has expired");
            }

            String clientId = (String) claims.getOrDefault("client_id", claims.get("sub"));
            if (clientId == null || clientId.isBlank()) {
                throw new UnauthorizedClientAccessException("Invalid JWT token: missing 'client_id' claim");
            }

            String issuer = (String) claims.getOrDefault("iss", ISSUER);
            return new ValidatedToken(true, clientId.trim(), issuer, issuedAt, expiresAt);
        } catch (UnauthorizedClientAccessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnauthorizedClientAccessException("Malformed JWT payload: " + ex.getMessage());
        }
    }

    private String signHmacSha256(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKey = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            return base64UrlEncode(rawHmac);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 for JWT", ex);
        }
    }

    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record GeneratedToken(
            String accessToken,
            String tokenType,
            long expiresIn,
            String clientId,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }

    public record ValidatedToken(
            boolean valid,
            String clientId,
            String issuer,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }
}

