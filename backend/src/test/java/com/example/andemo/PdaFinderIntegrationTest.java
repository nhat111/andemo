package com.example.andemo;

import com.example.andemo.dto.LoginResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PdaFinderIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void login() {
        adminToken = loginAs("admin");
        userToken = loginAs("user");
    }

    @Test
    void createdAlertIsPushedToConnectedPda() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        WebSocketSession session = connect("pda-ws-1", userToken, received);
        try {
            waitUntilOnline("pda-ws-1");

            JsonNode created = createAlert(Map.of("deviceId", "pda-ws-1", "message", "Tìm máy kho A"));

            String pushed = received.poll(5, TimeUnit.SECONDS);
            assertThat(pushed).as("message pushed over WebSocket").isNotNull();
            JsonNode command = objectMapper.readTree(pushed);
            assertThat(command.get("type").asText()).isEqualTo("PDA_FINDER_ALERT");
            assertThat(command.get("requestId").asText()).isEqualTo(created.get("requestId").asText());
            assertThat(command.get("message").asText()).isEqualTo("Tìm máy kho A");
        } finally {
            session.close();
        }
    }

    @Test
    void alertForAnotherPdaIsNotPushed() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        WebSocketSession session = connect("pda-ws-2", userToken, received);
        try {
            waitUntilOnline("pda-ws-2");
            createAlert(Map.of("deviceId", "some-other-pda"));
            assertThat(received.poll(1, TimeUnit.SECONDS)).isNull();
        } finally {
            session.close();
        }
    }

    @Test
    void handshakeWithoutTokenIsRejected() {
        assertThatThrownBy(() -> connect("pda-no-token", null, new LinkedBlockingQueue<>()))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    void offlinePdaGetsAlertFromPendingThenAckRemovesIt() {
        String requestId = createAlert(Map.of("deviceId", "pda-offline")).get("requestId").asText();

        assertThat(pendingRequestIds("pda-offline")).contains(requestId);
        assertThat(pendingRequestIds("pda-someone-else")).doesNotContain(requestId);

        assertThat(ack(requestId, "pda-offline", "DELIVERED")).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(pendingRequestIds("pda-offline")).doesNotContain(requestId);
    }

    @Test
    void statusNeverGoesBackwards() {
        String requestId = createAlert(Map.of("deviceId", "pda-status")).get("requestId").asText();

        ack(requestId, "pda-status", "STOPPED_BY_USER");
        // Ack DELIVERED đến muộn (lần gửi trước thất bại, PDA gửi lại): không được ghi đè
        assertThat(ack(requestId, "pda-status", "DELIVERED")).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(statusOf(requestId)).isEqualTo("STOPPED_BY_USER");
    }

    @Test
    void ackForUnknownAlertOrWrongPdaIsNotFound() {
        String requestId = createAlert(Map.of("deviceId", "pda-owner")).get("requestId").asText();

        assertThat(ack("REQ-does-not-exist", "pda-owner", "DELIVERED")).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ack(requestId, "pda-intruder", "DELIVERED")).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void expiredAlertIsNotPending() throws InterruptedException {
        String requestId = createAlert(Map.of("deviceId", "pda-expire", "ttlSeconds", 1)).get("requestId").asText();
        Thread.sleep(1500);
        assertThat(pendingRequestIds("pda-expire")).doesNotContain(requestId);
    }

    @Test
    void broadcastAlertIsPendingForEveryPda() {
        String requestId = createAlert(Map.of("message", "Tìm mọi máy")).get("requestId").asText();
        assertThat(pendingRequestIds("pda-any-1")).contains(requestId);
        assertThat(pendingRequestIds("pda-any-2")).contains(requestId);
    }

    @Test
    void onlyAdminCanCreateAlerts() {
        ResponseEntity<String> response = rest.exchange("/api/pda/alerts", HttpMethod.POST,
                new HttpEntity<>(Map.of("deviceId", "x"), authHeaders(userToken)), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ----- helpers -----

    private String loginAs(String username) {
        ResponseEntity<LoginResponse> response =
                rest.postForEntity("/api/auth/login", Map.of("username", username, "password", "123456"), LoginResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().getToken();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private JsonNode createAlert(Map<String, Object> body) {
        ResponseEntity<JsonNode> response = rest.exchange("/api/pda/alerts", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(adminToken)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private List<String> pendingRequestIds(String deviceId) {
        ResponseEntity<JsonNode> response = rest.exchange("/api/pda/alerts/pending?deviceId=" + deviceId,
                HttpMethod.GET, new HttpEntity<>(authHeaders(userToken)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().findValuesAsText("requestId");
    }

    private HttpStatusCode ack(String requestId, String deviceId, String status) {
        return rest.exchange("/api/pda/alerts/" + requestId + "/ack", HttpMethod.POST,
                new HttpEntity<>(Map.of("deviceId", deviceId, "status", status), authHeaders(userToken)),
                Void.class).getStatusCode();
    }

    private String statusOf(String requestId) {
        JsonNode all = rest.exchange("/api/pda/alerts", HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)), JsonNode.class).getBody();
        for (JsonNode alert : all) {
            if (alert.get("requestId").asText().equals(requestId)) {
                return alert.get("status").asText();
            }
        }
        throw new AssertionError("Alert not found: " + requestId);
    }

    private WebSocketSession connect(String deviceId, String token, BlockingQueue<String> received)
            throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        TextWebSocketHandler handler = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                received.add(message.getPayload());
            }
        };
        URI uri = URI.create("ws://localhost:" + port + "/ws/pda?deviceId=" + deviceId);
        return new StandardWebSocketClient().execute(handler, headers, uri).get(5, TimeUnit.SECONDS);
    }

    private void waitUntilOnline(String deviceId) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            JsonNode online = rest.exchange("/api/pda/devices/online", HttpMethod.GET,
                    new HttpEntity<>(authHeaders(adminToken)), JsonNode.class).getBody();
            for (JsonNode id : online) {
                if (id.asText().equals(deviceId)) {
                    return;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("PDA never came online: " + deviceId);
    }
}
