package com.cobre.notification.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-client shared secrets used to authenticate against {@code POST /auth/token}
 * (cobre.security.client-secrets.CLIENT001=...). Override them with environment variables in any non-demo setup.
 */
@Configuration
@ConfigurationProperties(prefix = "cobre.security")
public class ClientCredentialsProperties {

    private Map<String, String> clientSecrets = new HashMap<>();

    public Map<String, String> getClientSecrets() {
        return clientSecrets;
    }

    public void setClientSecrets(Map<String, String> clientSecrets) {
        this.clientSecrets = clientSecrets;
    }
}

