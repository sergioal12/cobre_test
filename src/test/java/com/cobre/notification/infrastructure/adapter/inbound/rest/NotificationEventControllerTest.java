package com.cobre.notification.infrastructure.adapter.inbound.rest;

import com.cobre.notification.application.port.outbound.SubscriptionPort;
import com.cobre.notification.infrastructure.security.JwtTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "cobre.notification.webhook.allow-insecure-localhost=true",
        "cobre.notification.webhook.max-attempts=5",
        "cobre.notification.webhook.initial-backoff-ms=5"
})
@AutoConfigureMockMvc
@Transactional
class NotificationEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private SubscriptionPort subscriptionPort;

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start(InetAddress.getByName("127.0.0.1"), 0);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    private String bearer(String clientId) {
        return "Bearer " + jwtTokenService.generateToken(clientId).accessToken();
    }

    // ---------------------------------------------------------------- functional requirements

    @Test
    @DisplayName("POST /notification_events should create and dispatch a manual event of any type")
    void shouldCreateAndDispatchNewEventOfCustomType() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
        String targetWebhookUrl = "http://127.0.0.1:" + mockWebServer.getPort() + "/webhook/manual";

        mockMvc.perform(post("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CLIENT001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_id": "EVT-MANUAL-001",
                                  "event_type": "balance_update",
                                  "content": "Account balance updated to $18,450.75",
                                  "webhook_url": "%s"
                                }
                                """.formatted(targetWebhookUrl)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.event_id", is("EVT-MANUAL-001")))
                .andExpect(jsonPath("$.event_type", is("balance_update")))
                .andExpect(jsonPath("$.client_id", is("CLIENT001")))
                .andExpect(jsonPath("$.delivery_status", is("completed")))
                .andExpect(jsonPath("$.attempt_count", is(1)));
    }

    @Test
    @DisplayName("GET /notification_events should return events filtered by delivery_status and date for the authenticated client")
    void shouldListAndFilterClientNotificationEvents() throws Exception {
        mockMvc.perform(get("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CLIENT001"))
                        .param("delivery_status", "completed")
                        .param("creation_date", "2024-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].event_id", is("EVT001")))
                .andExpect(jsonPath("$[0].client_id", is("CLIENT001")))
                .andExpect(jsonPath("$[0].delivery_status", is("completed")));
    }

    @Test
    @DisplayName("GET /notification_events with invalid filters should return 400")
    void shouldRejectInvalidFilters() throws Exception {
        mockMvc.perform(get("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CLIENT001"))
                        .param("delivery_status", "bogus"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CLIENT001"))
                        .param("from_date", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /notification_events/dlq should return DLQ entries for the authenticated client")
    void shouldListClientDlqEntries() throws Exception {
        mockMvc.perform(get("/notification_events/dlq")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CLIENT003")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].client_id", is("CLIENT003")))
                .andExpect(jsonPath("$[0].retry_attempts_exhausted", is(5)))
                .andExpect(jsonPath("$[0].reprocess_status", is("pending_manual_reprocess")));
    }

    @Test
    @DisplayName("GET /notification_events/{id} should return 200 for owner and 404 when accessed by another client")
    void shouldGetSingleEventDetailsAndEnforceOwnership() throws Exception {
        mockMvc.perform(get("/notification_events/EVT001")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CLIENT001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_id", is("EVT001")))
                .andExpect(jsonPath("$.event_type", is("credit_card_payment")))
                .andExpect(jsonPath("$.delivery_attempts", hasSize(1)));

        mockMvc.perform(get("/notification_events/EVT001")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CLIENT002")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /notification_events/{id}/replay should replay a failed event to the webhook and return 409 for completed events")
    void shouldReplayFailedEventAndRejectCompletedEvent() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
        String targetWebhookUrl = "http://127.0.0.1:" + mockWebServer.getPort() + "/webhook/replay";

        mockMvc.perform(post("/notification_events/EVT005/replay")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CLIENT003"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"webhook_url\":\"" + targetWebhookUrl + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_id", is("EVT005")))
                .andExpect(jsonPath("$.delivery_status", is("completed")))
                .andExpect(jsonPath("$.attempt_count", is(2)))
                .andExpect(jsonPath("$.delivery_attempts", hasSize(2)));

        mockMvc.perform(post("/notification_events/EVT006/replay")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CLIENT003")))
                .andExpect(status().isConflict());
    }

    // ---------------------------------------------------------------- authentication

    @Test
    @DisplayName("Every /notification_events endpoint should return 401 without a JWT, and X-Client-Id alone must NOT authenticate")
    void shouldRequireJwtOnAllEndpoints() throws Exception {
        mockMvc.perform(get("/notification_events")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/notification_events/EVT001")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/notification_events/dlq")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/notification_events/EVT003/replay")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/notification_events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event_type\":\"t\",\"content\":\"c\",\"webhook_url\":\"https://attacker.example.com/h\"}"))
                .andExpect(status().isUnauthorized());

        // Impersonation through a plain, self-declared header is no longer accepted
        mockMvc.perform(get("/notification_events").header("X-Client-Id", "CLIENT002"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/notification_events").param("client_id", "CLIENT002"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Tampered, malformed, alg=none and non-Bearer credentials should be rejected with 401")
    void shouldRejectForgedCredentials() throws Exception {
        String valid = jwtTokenService.generateToken("CLIENT001").accessToken();

        mockMvc.perform(get("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + valid + "tampered"))
                .andExpect(status().isUnauthorized());

        // Payload swapped to CLIENT002 keeping the original signature
        String[] parts = valid.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"CLIENT002\",\"client_id\":\"CLIENT002\",\"iss\":\"cobre-notification-service\",\"exp\":"
                        + (Instant.now().getEpochSecond() + 3600) + "}").getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(get("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + parts[0] + "." + forgedPayload + "." + parts[2]))
                .andExpect(status().isUnauthorized());

        // Unsigned token with alg=none
        String noneHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(get("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + noneHeader + "." + forgedPayload + "."))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, "Basic Y2xpZW50MDAxOnNlY3JldA=="))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/token should only issue tokens for valid (client_id, client_secret) pairs")
    void shouldIssueTokensOnlyForValidClientCredentials() throws Exception {
        // wrong secret, unknown client and missing secret -> never a token
        mockMvc.perform(post("/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_id\":\"CLIENT002\",\"client_secret\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_id\":\"CLIENT999\",\"client_secret\":\"client001-demo-secret\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_id\":\"CLIENT002\"}"))
                .andExpect(status().isBadRequest());
        // a client cannot use another client's secret
        mockMvc.perform(post("/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_id\":\"CLIENT002\",\"client_secret\":\"client001-demo-secret\"}"))
                .andExpect(status().isUnauthorized());

        MvcResult result = mockMvc.perform(post("/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_id\":\"CLIENT001\",\"client_secret\":\"client001-demo-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type", is("Bearer")))
                .andExpect(jsonPath("$.client_id", is("CLIENT001")))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = body.get("access_token").asText();

        mockMvc.perform(get("/auth/validate").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.client_id", is("CLIENT001")))
                .andExpect(jsonPath("$.issuer", is("cobre-notification-service")));

        mockMvc.perform(get("/notification_events").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].client_id", is("CLIENT001")));
    }

    // ---------------------------------------------------------------- multi-tenant isolation (BOLA)

    @Test
    @DisplayName("A client_id that differs from the authenticated client should be rejected with 403 on every endpoint")
    void shouldRejectCrossTenantClientIdParameter() throws Exception {
        String token = bearer("CLIENT001");

        mockMvc.perform(get("/notification_events").header(HttpHeaders.AUTHORIZATION, token)
                        .param("client_id", "CLIENT002"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/notification_events/dlq").header(HttpHeaders.AUTHORIZATION, token)
                        .param("client_id", "CLIENT002"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/notification_events/EVT003").header(HttpHeaders.AUTHORIZATION, token)
                        .param("client_id", "CLIENT002"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/notification_events/EVT003/replay").header(HttpHeaders.AUTHORIZATION, token)
                        .param("client_id", "CLIENT002"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /notification_events must not let a client create events for, or redirect the webhook of, another client")
    void shouldBlockCrossTenantWritesAndWebhookHijack() throws Exception {
        String originalWebhook = subscriptionPort.findActiveSubscriptionByClientId("CLIENT002")
                .orElseThrow().webhookUrl();

        mockMvc.perform(post("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CLIENT001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_id": "EVT-HIJACK",
                                  "event_type": "balance_update",
                                  "content": "x",
                                  "client_id": "CLIENT002",
                                  "webhook_url": "https://attacker.example.com/hook"
                                }
                                """))
                .andExpect(status().isForbidden());

        assertThat(subscriptionPort.findActiveSubscriptionByClientId("CLIENT002").orElseThrow().webhookUrl())
                .isEqualTo(originalWebhook);
        mockMvc.perform(get("/notification_events/EVT-HIJACK")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CLIENT002")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /notification_events with an existing event_id must return 409 and never overwrite the original event")
    void shouldNotOverwriteExistingEventOfAnotherClient() throws Exception {
        mockMvc.perform(post("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CLIENT001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event_id\":\"EVT003\",\"event_type\":\"balance_update\",\"content\":\"overwrite attempt\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/notification_events/EVT003")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CLIENT002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client_id", is("CLIENT002")))
                .andExpect(jsonPath("$.event_type", is("credit_transfer")));
    }

    // ---------------------------------------------------------------- input validation & error handling

    @Test
    @DisplayName("POST /notification_events should reject oversized, blank or malformed payloads with 400 (never 403/500)")
    void shouldValidatePayloads() throws Exception {
        String token = bearer("CLIENT001");
        String tooLong = "A".repeat(1001);

        mockMvc.perform(post("/notification_events").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event_type\":\"t\",\"content\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/notification_events").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event_type\":\"\",\"content\":\"c\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/notification_events").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }
}
