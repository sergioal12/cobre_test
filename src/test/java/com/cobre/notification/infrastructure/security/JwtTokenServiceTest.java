package com.cobre.notification.infrastructure.security;

import com.cobre.notification.domain.exception.UnauthorizedClientAccessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String SECRET = "unit-test-secret-key-with-more-than-32-bytes";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtTokenService service = new JwtTokenService(objectMapper, SECRET, 3600);

    @Test
    @DisplayName("Generated tokens should round-trip with the right claims and 3 parts")
    void shouldGenerateAndValidateToken() {
        JwtTokenService.GeneratedToken generated = service.generateToken("CLIENT001");

        assertThat(generated.accessToken().split("\\.")).hasSize(3);
        assertThat(generated.expiresIn()).isEqualTo(3600);

        JwtTokenService.ValidatedToken validated = service.validateToken(generated.accessToken());
        assertThat(validated.valid()).isTrue();
        assertThat(validated.clientId()).isEqualTo("CLIENT001");
        assertThat(validated.issuer()).isEqualTo("cobre-notification-service");
        assertThat(validated.expiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("Tokens signed with another secret or with a tampered payload should be rejected")
    void shouldRejectTamperedAndForeignTokens() {
        String foreign = new JwtTokenService(objectMapper, "another-secret-key-with-more-than-32-bytes!!", 3600)
                .generateToken("CLIENT001").accessToken();
        assertThatThrownBy(() -> service.validateToken(foreign))
                .isInstanceOf(UnauthorizedClientAccessException.class)
                .hasMessageContaining("signature");

        String[] parts = service.generateToken("CLIENT001").accessToken().split("\\.");
        String swapped = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"client_id\":\"CLIENT002\",\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.validateToken(parts[0] + "." + swapped + "." + parts[2]))
                .isInstanceOf(UnauthorizedClientAccessException.class);
    }

    @Test
    @DisplayName("Expired tokens should be rejected even when the signature is valid")
    void shouldRejectExpiredToken() throws Exception {
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = b64("{\"client_id\":\"CLIENT001\",\"iss\":\"cobre-notification-service\","
                + "\"iat\":1000,\"exp\":" + (Instant.now().getEpochSecond() - 60) + "}");
        String token = header + "." + payload + "." + sign(header + "." + payload);

        assertThatThrownBy(() -> service.validateToken(token))
                .isInstanceOf(UnauthorizedClientAccessException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Correctly signed tokens using an algorithm other than HS256 should be rejected")
    void shouldRejectUnsupportedAlgorithm() throws Exception {
        String header = b64("{\"alg\":\"HS512\",\"typ\":\"JWT\"}");
        String payload = b64("{\"client_id\":\"CLIENT001\",\"exp\":" + (Instant.now().getEpochSecond() + 600) + "}");
        String token = header + "." + payload + "." + sign(header + "." + payload);

        assertThatThrownBy(() -> service.validateToken(token))
                .isInstanceOf(UnauthorizedClientAccessException.class)
                .hasMessageContaining("algorithm");
    }

    @Test
    @DisplayName("Empty, malformed or unsigned tokens should be rejected")
    void shouldRejectMalformedTokens() {
        assertThatThrownBy(() -> service.validateToken(null)).isInstanceOf(UnauthorizedClientAccessException.class);
        assertThatThrownBy(() -> service.validateToken("  ")).isInstanceOf(UnauthorizedClientAccessException.class);
        assertThatThrownBy(() -> service.validateToken("a.b")).isInstanceOf(UnauthorizedClientAccessException.class);
        assertThatThrownBy(() -> service.validateToken("a.b.")).isInstanceOf(UnauthorizedClientAccessException.class);
    }

    @Test
    @DisplayName("A signing secret shorter than 32 bytes should fail fast at startup")
    void shouldRejectWeakSecret() {
        assertThatThrownBy(() -> new JwtTokenService(objectMapper, "too-short", 3600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    private String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String signingInput) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    }
}

