package com.cobre.notification.infrastructure.adapter.inbound.rest;

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

    @Test
    @DisplayName("POST /notification_events should create and dispatch a manual event of any type")
    void shouldCreateAndDispatchNewEventOfCustomType() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
        String targetWebhookUrl = "http://127.0.0.1:" + mockWebServer.getPort() + "/webhook/manual";

        mockMvc.perform(post("/notification_events")
                        .header("X-Client-Id", "CLIENT001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_id": "EVT-MANUAL-001",
                                  "event_type": "balance_update",
                                  "content": "Account balance updated to $18,450.75",
                                  "client_id": "CLIENT001",
                                  "webhook_url": "%s"
                                }
                                """.formatted(targetWebhookUrl)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.event_id", is("EVT-MANUAL-001")))
                .andExpect(jsonPath("$.event_type", is("balance_update")))
                .andExpect(jsonPath("$.delivery_status", is("completed")))
                .andExpect(jsonPath("$.attempt_count", is(1)));
    }

    @Test
    @DisplayName("GET /notification_events should return events filtered by client_id, delivery_status, and date")
    void shouldListAndFilterClientNotificationEvents() throws Exception {
        mockMvc.perform(get("/notification_events")
                        .header("X-Client-Id", "CLIENT001")
                        .param("delivery_status", "completed")
                        .param("creation_date", "2024-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].event_id", is("EVT001")))
                .andExpect(jsonPath("$[0].client_id", is("CLIENT001")))
                .andExpect(jsonPath("$[0].delivery_status", is("completed")));
    }

    @Test
    @DisplayName("GET /notification_events/dlq should return DLQ entries for the authenticated client")
    void shouldListClientDlqEntries() throws Exception {
        mockMvc.perform(get("/notification_events/dlq")
                        .header("X-Client-Id", "CLIENT003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].client_id", is("CLIENT003")))
                .andExpect(jsonPath("$[0].retry_attempts_exhausted", is(5)))
                .andExpect(jsonPath("$[0].reprocess_status", is("pending_manual_reprocess")));
    }

    @Test
    @DisplayName("GET /notification_events should return 401 when client_id is missing and 403 on cross-tenant mismatch")
    void shouldRejectMissingOrMismatchedClientId() throws Exception {
        mockMvc.perform(get("/notification_events"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/notification_events")
                        .header("X-Client-Id", "CLIENT001")
                        .param("client_id", "CLIENT002"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /notification_events/{id} should return 200 for owner and 404 when accessed by another client")
    void shouldGetSingleEventDetailsAndEnforceOwnership() throws Exception {
        mockMvc.perform(get("/notification_events/EVT001")
                        .header("X-Client-Id", "CLIENT001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_id", is("EVT001")))
                .andExpect(jsonPath("$.event_type", is("credit_card_payment")))
                .andExpect(jsonPath("$.delivery_attempts", hasSize(1)));

        mockMvc.perform(get("/notification_events/EVT001")
                        .header("X-Client-Id", "CLIENT002"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /notification_events/{id}/replay should replay a failed event to the webhook and return 409 for completed events")
    void shouldReplayFailedEventAndRejectCompletedEvent() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
        String targetWebhookUrl = "http://127.0.0.1:" + mockWebServer.getPort() + "/webhook/replay";

        mockMvc.perform(post("/notification_events/EVT005/replay")
                        .header("X-Client-Id", "CLIENT003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"webhook_url\":\"" + targetWebhookUrl + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_id", is("EVT005")))
                .andExpect(jsonPath("$.delivery_status", is("completed")))
                .andExpect(jsonPath("$.attempt_count", is(2)))
                .andExpect(jsonPath("$.delivery_attempts", hasSize(2)));

        // Replaying an already completed event should return 409 Conflict
        mockMvc.perform(post("/notification_events/EVT006/replay")
                        .header("X-Client-Id", "CLIENT003"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /auth/token should generate a valid HS256 JWT token and authenticate /notification_events requests")
    void shouldGenerateValidateAndAuthenticateWithJwtToken() throws Exception {
        MvcResult tokenResult = mockMvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_id\":\"CLIENT001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type", is("Bearer")))
                .andExpect(jsonPath("$.client_id", is("CLIENT001")))
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
        String accessToken = jsonNode.get("access_token").asText();

        // Validate JWT token via GET /auth/validate
        mockMvc.perform(get("/auth/validate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.client_id", is("CLIENT001")))
                .andExpect(jsonPath("$.issuer", is("cobre-notification-service")));

        // Query /notification_events using ONLY the Bearer JWT token
        mockMvc.perform(get("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].client_id", is("CLIENT001")));

        // Cross-tenant attempt (JWT for CLIENT001 but query param client_id=CLIENT002) -> 403 Forbidden
        mockMvc.perform(get("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("client_id", "CLIENT002"))
                .andExpect(status().isForbidden());

        // Tampered JWT signature -> 401 Unauthorized
        mockMvc.perform(get("/notification_events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken + "tampered"))
                .andExpect(status().isUnauthorized());
    }
}
