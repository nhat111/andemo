package com.example.andemo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Task 17 (xác nhận phiếu hủy) + Task 18 (cập nhật tồn kho). Dữ liệu mẫu: DisposalDataInitializer. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // mỗi test 1 DB mới (có trừ tồn)
class DisposalIntegrationTest {

    private static final String MILK = "8851993123456";
    private static final String NOODLE = "8934567890123";
    private static final String COFFEE = "8936036020151";

    @Autowired
    private TestRestTemplate rest;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void login() {
        adminToken = loginAs("admin");
        userToken = loginAs("user");
    }

    // ---------- Inquiry ----------

    @Test
    void listFiltersByStatus() {
        JsonNode requested = get("/api/disposals?status=REQUESTED", userToken).getBody();
        assertThat(requested).hasSize(3);
        assertThat(requested.get(0).get("disposalNo").asText()).isEqualTo("DSP-20260929-001"); // mới nhất trước
        assertThat(requested.get(0).get("lineCount").asInt()).isEqualTo(2);
        assertThat(requested.get(0).get("totalQty").asLong()).isEqualTo(15);

        assertThat(get("/api/disposals", userToken).getBody()).hasSize(5);
        assertThat(get("/api/disposals?status=CANCELLED", userToken).getBody()).hasSize(1);
        assertThat(get("/api/disposals?status=abc", userToken).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------- Detail ----------

    @Test
    void detailShowsAvailableQtyAndWhyNotConfirmable() {
        JsonNode ok = get("/api/disposals/DSP-20260929-001", userToken).getBody();
        assertThat(ok.get("confirmable").asBoolean()).isTrue();
        assertThat(ok.get("items").get(1).get("availableQty").asLong()).as("mì: tồn 85 − giữ chỗ 10").isEqualTo(75);

        JsonNode short_ = get("/api/disposals/DSP-20260929-003", userToken).getBody();
        assertThat(short_.get("confirmable").asBoolean()).isFalse();
        assertThat(short_.get("issues")).hasSize(2); // cà phê + dầu ăn
        assertThat(short_.get("items").get(0).get("sufficient").asBoolean()).isFalse();
        assertThat(short_.get("items").get(2).get("sufficient").asBoolean()).as("bánh quy đủ").isTrue();

        JsonNode done = get("/api/disposals/DSP-20260928-001", userToken).getBody();
        assertThat(done.get("confirmable").asBoolean()).isFalse();
        assertThat(done.get("confirmedBy").asText()).isEqualTo("admin");

        assertThat(get("/api/disposals/KHONG-CO", userToken).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- Confirm + inventory ----------

    @Test
    void confirmDeductsInventoryAndWritesHistory() {
        long version = get("/api/disposals/DSP-20260929-001", adminToken).getBody().get("version").asLong();

        ResponseEntity<JsonNode> res = confirm("DSP-20260929-001", version, adminToken);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(res.getBody().get("confirmedBy").asText()).isEqualTo("admin");
        assertThat(onHand(MILK)).isEqualTo(40 - 5);
        assertThat(onHand(NOODLE)).isEqualTo(85 - 10);

        JsonNode history = get("/api/inventory/" + MILK + "/transactions", userToken).getBody();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("txType").asText()).isEqualTo("DISPOSAL");
        assertThat(history.get(0).get("qtyChange").asLong()).isEqualTo(-5);
        assertThat(history.get(0).get("beforeQty").asLong()).isEqualTo(40);
        assertThat(history.get(0).get("afterQty").asLong()).isEqualTo(35);
        assertThat(history.get(0).get("refNo").asText()).isEqualTo("DSP-20260929-001");
    }

    @Test
    void confirmTwiceIsRejectedAndDeductsOnlyOnce() {
        assertThat(confirm("DSP-20260929-001", null, adminToken).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> again = confirm("DSP-20260929-001", null, adminToken);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().get("code").asText()).isEqualTo("INVALID_STATUS");
        assertThat(onHand(MILK)).isEqualTo(35);
        assertThat(get("/api/inventory/" + MILK + "/transactions", userToken).getBody()).hasSize(1);
    }

    @Test
    void insufficientQtyRejectsWholeDisposalWithoutAnyChange() {
        ResponseEntity<JsonNode> res = confirm("DSP-20260929-003", null, adminToken);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().get("code").asText()).isEqualTo("INSUFFICIENT_QTY");
        assertThat(res.getBody().get("details")).hasSize(2);
        assertThat(res.getBody().get("details").get(0).asText()).contains("khả dụng 20");
        // Bánh quy (dòng đủ tồn) cũng KHÔNG bị trừ: cả phiếu rollback
        assertThat(onHand("8801234567890")).isEqualTo(60);
        assertThat(onHand(COFFEE)).isEqualTo(25);
        assertThat(get("/api/disposals/DSP-20260929-003", userToken).getBody().get("status").asText())
                .isEqualTo("REQUESTED");
    }

    @Test
    void cancelledDisposalCannotBeConfirmed() {
        ResponseEntity<JsonNode> res = confirm("DSP-20260927-001", null, adminToken);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().get("message").asText()).contains("đã bị hủy");
    }

    @Test
    void staleVersionIsRejected() {
        ResponseEntity<JsonNode> res = confirm("DSP-20260929-002", 999L, adminToken);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().get("code").asText()).isEqualTo("STALE_DATA");
        assertThat(onHand("8901234567890")).isEqualTo(120);
    }

    @Test
    void onlyAdminCanConfirm() {
        ResponseEntity<JsonNode> res = confirm("DSP-20260929-002", null, userToken);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(onHand("8901234567890")).isEqualTo(120);
    }

    @Test
    void twoManagersConfirmingAtTheSameTimeDeductOnlyOnce() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<HttpStatusCode> task = () -> {
            start.await();
            return confirm("DSP-20260929-001", null, adminToken).getStatusCode();
        };
        var f1 = pool.submit(task);
        var f2 = pool.submit(task);
        start.countDown();
        var statuses = java.util.List.of(f1.get(), f2.get());
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        assertThat(onHand(MILK)).isEqualTo(35);
        assertThat(get("/api/inventory/" + MILK + "/transactions", userToken).getBody()).hasSize(1);
    }

    @Test
    void requiresLogin() {
        assertThat(get("/api/disposals", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private long onHand(String itemCode) {
        return get("/api/inventory/" + itemCode, userToken).getBody().get("onHandQty").asLong();
    }

    private ResponseEntity<JsonNode> confirm(String no, Long version, String token) {
        HttpHeaders headers = headers(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = version == null ? Map.of() : Map.of("version", version);
        return rest.exchange("/api/disposals/" + no + "/confirm", HttpMethod.POST, new HttpEntity<>(body, headers),
                JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String url, String token) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers(token)), JsonNode.class);
    }

    private static HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private String loginAs(String username) {
        ResponseEntity<JsonNode> res = rest.postForEntity("/api/auth/login",
                Map.of("username", username, "password", "123456"), JsonNode.class);
        return res.getBody().get("token").asText();
    }
}
