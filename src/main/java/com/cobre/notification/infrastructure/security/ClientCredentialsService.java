package com.cobre.notification.infrastructure.security;

import com.cobre.notification.domain.exception.UnauthorizedClientAccessException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Authenticates a client with its (client_id, client_secret) pair before a JWT is issued.
 */
@Component
public class ClientCredentialsService {

    private static final byte[] DUMMY_SECRET = "cobre-dummy-secret-for-constant-time".getBytes(StandardCharsets.UTF_8);

    private final Map<String, byte[]> secretsByClient = new HashMap<>();

    public ClientCredentialsService(ClientCredentialsProperties properties) {
        properties.getClientSecrets().forEach((clientId, secret) -> {
            if (clientId != null && secret != null && !secret.isBlank()) {
                secretsByClient.put(normalize(clientId), secret.getBytes(StandardCharsets.UTF_8));
            }
        });
    }

    /**
     * @return the canonical client_id when credentials are valid
     * @throws UnauthorizedClientAccessException with a generic message (no user enumeration) otherwise
     */
    public String authenticate(String clientId, String clientSecret) {
        byte[] expected = clientId != null ? secretsByClient.get(normalize(clientId)) : null;
        byte[] provided = clientSecret != null ? clientSecret.getBytes(StandardCharsets.UTF_8) : new byte[0];

        // Always run a comparison so unknown clients and wrong secrets are indistinguishable by timing.
        boolean matches = MessageDigest.isEqual(expected != null ? expected : DUMMY_SECRET, provided);
        if (expected == null || !matches) {
            throw new UnauthorizedClientAccessException("Invalid client credentials");
        }
        return normalize(clientId);
    }

    private String normalize(String clientId) {
        return clientId.trim().toUpperCase(Locale.ROOT);
    }
}

