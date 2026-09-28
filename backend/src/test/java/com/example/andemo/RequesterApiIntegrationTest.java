package com.example.andemo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** API cho requester (web /pda-finder.html và nút "Tìm PDA" trong app). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequesterApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void login() {
        adminToken = loginAs("admin");
        userToken = loginAs("user");
    }

    @Test
    void pollingPdaAppearsInDeviceListWithNameAndUser() {
        // TestRestTemplate tự mã hóa URL: truyền dấu cách thật, không truyền sẵn %20
        get("/api/pda/alerts/pending?deviceId=req-poll-1&deviceName=realme RMX1851", userToken);

        JsonNode device = findDevice("req-poll-1");
        assertThat(device.get("deviceName").asText()).isEqualTo("realme RMX1851");
        assertThat(device.get("username").asText()).isEqualTo("user");
        assertThat(device.get("online").asBoolean()).isTrue();
        assertThat(device.get("connected").asBoolean()).isFalse();
        assertThat(device.get("secondsSinceLastSeen").asLong()).isLessThan(5);
    }

    @Test
    void webSocketPdaIsConnectedAndStaysListedAfterDisconnect() throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setBearerAuth(userToken);
        URI uri = URI.create("ws://localhost:" + port + "/ws/pda?deviceId=req-ws-1&deviceName=Zebra%20TC21");
        WebSocketSession session = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler(), headers, uri).get(5, TimeUnit.SECONDS);

        JsonNode device = waitForDevice("req-ws-1", true);
        assertThat(device.get("deviceName").asText()).isEqualTo("Zebra TC21");
        assertThat(device.get("username").asText()).isEqualTo("user");
        assertThat(device.get("online").asBoolean()).isTrue();

        session.close();
        JsonNode after = waitForDevice("req-ws-1", false);
        assertThat(after.get("online").asBoolean()).as("just disconnected: still within the online window").isTrue();
    }

    @Test
    void alertCanBeFetchedByIdWithExpiredFlag() throws InterruptedException {
        JsonNode created = post("/api/pda/alerts", Map.of("deviceId", "req-get-1", "storeCode", "STORE01",
                "ttlSeconds", 1), adminToken).getBody();
        String requestId = created.get("requestId").asText();
        assertThat(created.get("requestedBy").asText()).isEqualTo("admin");
        assertThat(created.get("storeCode").asText()).isEqualTo("STORE01");
        assertThat(created.get("expired").asBoolean()).isFalse();

        Thread.sleep(1500);
        ResponseEntity<JsonNode> fetched = get("/api/pda/alerts/" + requestId, adminToken);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().get("status").asText()).isEqualTo("SENT");
        assertThat(fetched.getBody().get("expired").asBoolean()).isTrue();

        assertThat(get("/api/pda/alerts/REQ-missing", adminToken).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void requesterEndpointsAreAdminOnly() {
        assertThat(get("/api/pda/devices", userToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/pda/alerts", userToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/pda/alerts/REQ-any", userToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/pda/devices", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void webPageAndHealthArePublic() {
        ResponseEntity<String> page = rest.getForEntity("/pda-finder.html", String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("PDA Finder");

        ResponseEntity<String> home = rest.getForEntity("/", String.class);
        assertThat(home.getStatusCode().is3xxRedirection() || home.getBody().contains("PDA Finder")).isTrue();

        ResponseEntity<String> health = rest.getForEntity("/api/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("UP");
    }

    // ----- helpers -----

    private JsonNode findDevice(String deviceId) {
        for (JsonNode device : get("/api/pda/devices", adminToken).getBody()) {
            if (device.get("deviceId").asText().equals(deviceId)) {
                return device;
            }
        }
        throw new AssertionError("Device not listed: " + deviceId);
    }

    private JsonNode waitForDevice(String deviceId, boolean connected) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            try {
                JsonNode device = findDevice(deviceId);
                if (device.get("connected").asBoolean() == connected) {
                    return device;
                }
            } catch (AssertionError notYet) {
                // chưa có
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Device " + deviceId + " never reached connected=" + connected);
    }

    private String loginAs(String username) {
        return rest.postForEntity("/api/auth/login", Map.of("username", username, "password", "123456"),
                JsonNode.class).getBody().get("token").asText();
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<JsonNode> get(String url, String token) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> post(String url, Object body, String token) {
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers(token)), JsonNode.class);
    }
}
