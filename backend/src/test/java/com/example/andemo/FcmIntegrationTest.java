package com.example.andemo;

import com.example.andemo.push.PushSender;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Luồng FCM với PushSender giả: không cần Firebase thật. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FcmIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @MockBean
    private PushSender pushSender;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        when(pushSender.isEnabled()).thenReturn(true);
        adminToken = loginAs("admin");
        userToken = loginAs("user");
    }

    @Test
    void alertIsSentViaFcmToTheRegisteredToken() {
        registerToken("fcm-pda-1", "token-pda-1");

        String requestId = createAlert(Map.of("deviceId", "fcm-pda-1", "message", "Tìm máy kho A",
                "storeCode", "STORE01", "ttlSeconds", 90));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(pushSender).send(eq("token-pda-1"), data.capture(), ttl.capture(), any());
        assertThat(data.getValue())
                .containsEntry("type", "PDA_FINDER_ALERT")
                .containsEntry("requestId", requestId)
                .containsEntry("message", "Tìm máy kho A")
                .containsEntry("storeCode", "STORE01")
                .containsKey("expiresAt");
        assertThat(ttl.getValue().getSeconds()).isBetween(85L, 90L);
    }

    @Test
    void noFcmForDevicesWithoutTokenAndNullStoreCodeIsOmitted() {
        get("/api/pda/alerts/pending?deviceId=fcm-no-token", userToken);
        createAlert(Map.of("deviceId", "fcm-no-token"));
        verify(pushSender, never()).send(any(), anyMap(), any(), any());

        registerToken("fcm-pda-2", "token-pda-2");
        createAlert(Map.of("deviceId", "fcm-pda-2"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
        verify(pushSender).send(eq("token-pda-2"), data.capture(), any(), any());
        assertThat(data.getValue()).doesNotContainKey("storeCode");
    }

    @Test
    void broadcastAlertGoesToEveryRegisteredToken() {
        registerToken("fcm-all-1", "token-all-1");
        registerToken("fcm-all-2", "token-all-2");

        createAlert(Map.of("message", "Tìm mọi máy"));

        verify(pushSender).send(eq("token-all-1"), anyMap(), any(), any());
        verify(pushSender).send(eq("token-all-2"), anyMap(), any(), any());
    }

    @Test
    void invalidTokenReportedByFcmIsRemoved() {
        registerToken("fcm-pda-3", "token-pda-3");
        assertThat(device("fcm-pda-3").get("fcm").asBoolean()).isTrue();

        createAlert(Map.of("deviceId", "fcm-pda-3"));
        ArgumentCaptor<Runnable> onInvalid = ArgumentCaptor.forClass(Runnable.class);
        verify(pushSender).send(eq("token-pda-3"), anyMap(), any(), onInvalid.capture());

        onInvalid.getValue().run(); // giả lập FCM trả UNREGISTERED

        assertThat(device("fcm-pda-3").get("fcm").asBoolean()).isFalse();
    }

    @Test
    void tokenMovesWhenAnotherDeviceRegistersItAndEmptyTokenUnregisters() {
        registerToken("fcm-old", "token-shared");
        registerToken("fcm-new", "token-shared");
        assertThat(device("fcm-old").get("fcm").asBoolean()).isFalse();
        assertThat(device("fcm-new").get("fcm").asBoolean()).isTrue();
        assertThat(device("fcm-new").get("deviceName").asText()).isEqualTo("Pixel test");
        assertThat(device("fcm-new").get("username").asText()).isEqualTo("user");

        registerToken("fcm-new", "");
        assertThat(device("fcm-new").get("fcm").asBoolean()).isFalse();
    }

    @Test
    void ackCountsAsContactForFcmDevices() throws InterruptedException {
        registerToken("fcm-ack", "token-ack");
        String requestId = createAlert(Map.of("deviceId", "fcm-ack"));
        long before = device("fcm-ack").get("secondsSinceLastSeen").asLong();
        Thread.sleep(1100);

        rest.exchange("/api/pda/alerts/" + requestId + "/ack", HttpMethod.POST,
                new HttpEntity<>(Map.of("deviceId", "fcm-ack", "status", "DELIVERED"), headers(userToken)),
                Void.class);

        assertThat(device("fcm-ack").get("secondsSinceLastSeen").asLong()).isLessThanOrEqualTo(before);
    }

    @Test
    void healthReportsFcmEnabled() {
        ResponseEntity<JsonNode> health = rest.getForEntity("/api/health", JsonNode.class);
        assertThat(health.getBody().get("fcm").asBoolean()).isTrue();
    }

    // ----- helpers -----

    private void registerToken(String deviceId, String token) {
        Map<String, String> body = new HashMap<>();
        body.put("fcmToken", token);
        body.put("deviceName", "Pixel test");
        ResponseEntity<Void> response = rest.exchange("/api/pda/devices/" + deviceId + "/fcm-token",
                HttpMethod.PUT, new HttpEntity<>(body, headers(userToken)), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private String createAlert(Map<String, Object> body) {
        ResponseEntity<JsonNode> response = rest.exchange("/api/pda/alerts", HttpMethod.POST,
                new HttpEntity<>(body, headers(adminToken)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("requestId").asText();
    }

    private JsonNode device(String deviceId) {
        for (JsonNode d : get("/api/pda/devices", adminToken).getBody()) {
            if (d.get("deviceId").asText().equals(deviceId)) {
                return d;
            }
        }
        throw new AssertionError("Device not listed: " + deviceId + " in " + List.of());
    }

    private ResponseEntity<JsonNode> get(String url, String token) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers(token)), JsonNode.class);
    }

    private String loginAs(String username) {
        return rest.postForEntity("/api/auth/login", Map.of("username", username, "password", "123456"),
                JsonNode.class).getBody().get("token").asText();
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
