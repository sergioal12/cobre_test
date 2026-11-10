package com.cobre.notification.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cobre.notification")
public class NotificationProperties {

    private String seedFile = "file:./notification_events.json";
    private String classpathSeedFile = "classpath:data/notification_events.json";
    private WebhookProperties webhook = new WebhookProperties();

    public String getSeedFile() {
        return seedFile;
    }

    public void setSeedFile(String seedFile) {
        this.seedFile = seedFile;
    }

    public String getClasspathSeedFile() {
        return classpathSeedFile;
    }

    public void setClasspathSeedFile(String classpathSeedFile) {
        this.classpathSeedFile = classpathSeedFile;
    }

    public WebhookProperties getWebhook() {
        return webhook;
    }

    public void setWebhook(WebhookProperties webhook) {
        this.webhook = webhook;
    }

    public static class WebhookProperties {
        private String defaultUrl = "https://postman-echo.com/post";
        private int maxAttempts = 5;
        private long initialBackoffMs = 200;
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 15000;
        private boolean allowInsecureLocalhost = false;
        private String signingSecret = "cobre-default-hmac-secret-key-2026";

        public String getDefaultUrl() {
            return defaultUrl;
        }

        public void setDefaultUrl(String defaultUrl) {
            this.defaultUrl = defaultUrl;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getInitialBackoffMs() {
            return initialBackoffMs;
        }

        public void setInitialBackoffMs(long initialBackoffMs) {
            this.initialBackoffMs = initialBackoffMs;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public boolean isAllowInsecureLocalhost() {
            return allowInsecureLocalhost;
        }

        public void setAllowInsecureLocalhost(boolean allowInsecureLocalhost) {
            this.allowInsecureLocalhost = allowInsecureLocalhost;
        }

        public String getSigningSecret() {
            return signingSecret;
        }

        public void setSigningSecret(String signingSecret) {
            this.signingSecret = signingSecret;
        }
    }
}
