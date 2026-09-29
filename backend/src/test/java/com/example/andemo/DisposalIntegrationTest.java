package com.example.andemo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 폐기 (hủy hàng) theo luồng bán lẻ Hàn Quốc: 등록 → 수정 / 취소 → 확정 (trừ tồn, 수불) → 확정취소, 마감.
 * Dữ liệu mẫu: DisposalDataInitializer (hôm qua đã 마감).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // mỗi test 1 DB mới
class DisposalIntegrationTest {

    private static final String MILK = "8851993123456";
    private static final String NOODLE = "8934567890123";
    private static final String WATER = "8901234567890";
    private static final String COOKIE = "8801234567890";
    private static final String COFFEE = "8936036020151";
    private static final String KIMBAP = "8801111222333";

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));
    private static final String D1 = no(TODAY, 1);        // đủ tồn, của user
    private static final String D2 = no(TODAY, 2);        // đủ tồn, của user2
    private static final String D3 = no(TODAY, 3);        // thiếu tồn
    private static final String Y1 = no(TODAY.minusDays(1), 1); // đã 확정, ngày đã 마감
    private static final String Y2 = no(TODAY.minusDays(1), 2); // đã 취소

    @Autowired
    private TestRestTemplate rest;

    private String admin;
    private String user;
    private String user2;

    @BeforeEach
    void login() {
        admin = loginAs("admin");
        user = loginAs("user");
        user2 = loginAs("user2");
    }

    // ================= Task 17: 폐기조회 / 상세 =================

    @Test
    void inquiryFiltersByStatusCodeAndBusinessDate() {
        JsonNode registered = get("/api/disposals?status=10", user).getBody();
        assertThat(registered).hasSize(3);
        assertThat(registered.get(0).get("disposalNo").asText()).isEqualTo(D3); // số phiếu mới nhất trước
        assertThat(registered.get(0).get("statusName").asText()).isEqualTo("등록");

        assertThat(get("/api/disposals?status=CONFIRMED", user).getBody()).hasSize(1);
        assertThat(get("/api/disposals", user).getBody()).hasSize(5);
        assertThat(get("/api/disposals?from=" + TODAY + "&to=" + TODAY, user).getBody()).hasSize(3);
        assertThat(get("/api/disposals?status=xyz", user).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        JsonNode d1 = registered.get(2);
        assertThat(d1.get("totalQty").asLong()).isEqualTo(6);
        // 3×800 + 2×1900 + 1×600 = 6,800 KRW giá vốn
        assertThat(d1.get("totalCostAmount").asLong()).isEqualTo(6_800);
    }

    @Test
    void detailShowsAvailableQtyIssuesAndAllowedActionsPerRole() {
        JsonNode asAdmin = get("/api/disposals/" + D3, admin).getBody();
        assertThat(asAdmin.get("issues")).hasSize(2); // cà phê + dầu ăn
        assertThat(asAdmin.get("items").get(0).get("availableQty").asLong()).isEqualTo(20);
        assertThat(asAdmin.get("items").get(0).get("sufficient").asBoolean()).isFalse();
        assertThat(asAdmin.get("items").get(0).get("reasonName").asText()).isEqualTo("리콜");
        assertThat(asAdmin.get("actions").get("confirm").asBoolean()).isTrue();

        JsonNode asOwner = get("/api/disposals/" + D1, user).getBody();
        assertThat(asOwner.get("actions").get("edit").asBoolean()).isTrue();
        assertThat(asOwner.get("actions").get("confirm").asBoolean()).as("점원 không xác nhận").isFalse();

        JsonNode asOther = get("/api/disposals/" + D1, user2).getBody();
        assertThat(asOther.get("actions").get("edit").asBoolean()).as("không phải người đăng ký").isFalse();

        JsonNode closedDay = get("/api/disposals/" + Y1, admin).getBody();
        assertThat(closedDay.get("closed").asBoolean()).isTrue();
        assertThat(closedDay.get("actions").get("cancelConfirm").asBoolean()).as("ngày đã 마감").isFalse();
    }

    // ================= 등록 / 수정 / 취소 =================

    @Test
    void registerCreatesNextSlipNumberWithPriceSnapshot() {
        ResponseEntity<JsonNode> res = post("/api/disposals", Map.of("remark", "Hủy cuối ca",
                "items", List.of(line(KIMBAP, 2, "EXPIRED"), line(WATER, 1, "DAMAGED"))), user);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode d = res.getBody();
        assertThat(d.get("disposalNo").asText()).isEqualTo(no(TODAY, 4));
        assertThat(d.get("statusCode").asText()).isEqualTo("10");
        assertThat(d.get("businessDate").asText()).isEqualTo(TODAY.toString());
        assertThat(d.get("items").get(0).get("costAmount").asLong()).isEqualTo(1_600);
        assertThat(d.get("totalSaleAmount").asLong()).isEqualTo(2 * 1500 + 900);
        assertThat(onHand(KIMBAP)).as("đăng ký chưa trừ tồn").isEqualTo(12);
    }

    @Test
    void registerValidatesEveryLineAtOnce() {
        ResponseEntity<JsonNode> res = post("/api/disposals", Map.of("items", List.of(
                line(KIMBAP, 2, "EXPIRED"),
                line(KIMBAP, 1, "EXPIRED"),   // trùng
                line("0000000000000", 1, "DAMAGED"),
                line(WATER, 0, "DAMAGED"),
                Map.of("itemCode", MILK, "qty", 1))), user); // thiếu lý do

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("code").asText()).isEqualTo("VALIDATION");
        assertThat(res.getBody().get("details")).hasSize(4);
        assertThat(post("/api/disposals", Map.of("items", List.of()), user).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void ownerCanEditRegisteredSlipOthersCannot() {
        long version = get("/api/disposals/" + D1, user).getBody().get("version").asLong();
        Map<String, Object> body = Map.of("version", version, "remark", "Sửa số lượng",
                "items", List.of(line(KIMBAP, 5, "EXPIRED")));

        assertThat(put("/api/disposals/" + D1, body, user2).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<JsonNode> res = put("/api/disposals/" + D1, body, user);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("items")).hasSize(1);
        assertThat(res.getBody().get("totalQty").asLong()).isEqualTo(5);
        assertThat(res.getBody().get("updatedBy").asText()).isEqualTo("user");

        assertThat(put("/api/disposals/" + D1, body, user).getBody().get("code").asText())
                .as("gửi lại version cũ").isEqualTo("STALE_DATA");
    }

    @Test
    void cancelRequiresReasonAndBlocksLaterActions() {
        assertThat(post("/api/disposals/" + D2 + "/cancel", Map.of(), user2).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        JsonNode d = post("/api/disposals/" + D2 + "/cancel", Map.of("reason", "Tìm lại được hàng"), user2).getBody();
        assertThat(d.get("statusCode").asText()).isEqualTo("90");
        assertThat(d.get("cancelReason").asText()).isEqualTo("Tìm lại được hàng");

        ResponseEntity<JsonNode> confirm = post("/api/disposals/" + D2 + "/confirm", Map.of(), admin);
        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(confirm.getBody().get("message").asText()).contains("취소");
        assertThat(onHand(WATER)).isEqualTo(120);
    }

    // ================= Task 17 + 18: 확정 =================

    @Test
    void confirmDeductsStockAndWritesLedgerWithCost() {
        long version = get("/api/disposals/" + D1, admin).getBody().get("version").asLong();

        JsonNode d = post("/api/disposals/" + D1 + "/confirm", Map.of("version", version), admin).getBody();

        assertThat(d.get("statusCode").asText()).isEqualTo("20");
        assertThat(d.get("confirmedBy").asText()).isEqualTo("admin");
        assertThat(d.get("actions").get("cancelConfirm").asBoolean()).isTrue();
        assertThat(onHand(KIMBAP)).isEqualTo(12 - 3);
        assertThat(onHand(MILK)).isEqualTo(40 - 2);
        assertThat(onHand(NOODLE)).isEqualTo(85 - 1);

        JsonNode ledger = get("/api/inventory/" + KIMBAP + "/transactions", user).getBody();
        assertThat(ledger).hasSize(1);
        assertThat(ledger.get(0).get("txType").asText()).isEqualTo("DISPOSAL");
        assertThat(ledger.get(0).get("qtyChange").asLong()).isEqualTo(-3);
        assertThat(ledger.get(0).get("beforeQty").asLong()).isEqualTo(12);
        assertThat(ledger.get(0).get("afterQty").asLong()).isEqualTo(9);
        assertThat(ledger.get(0).get("costAmount").asLong()).isEqualTo(-2_400);
        assertThat(ledger.get(0).get("businessDate").asText()).isEqualTo(TODAY.toString());
        assertThat(ledger.get(0).get("refNo").asText()).isEqualTo(D1);
    }

    @Test
    void insufficientAvailableQtyRejectsWholeSlip() {
        ResponseEntity<JsonNode> res = post("/api/disposals/" + D3 + "/confirm", Map.of(), admin);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().get("code").asText()).isEqualTo("INSUFFICIENT_QTY");
        assertThat(res.getBody().get("details")).hasSize(2);
        assertThat(res.getBody().get("details").get(0).asText()).contains("khả dụng 20");
        assertThat(onHand(COOKIE)).as("dòng đủ tồn cũng không bị trừ").isEqualTo(60);
        assertThat(onHand(COFFEE)).isEqualTo(25);
    }

    @Test
    void onlyManagerConfirmsAndOnlyOnce() {
        assertThat(post("/api/disposals/" + D2 + "/confirm", Map.of(), user2).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/api/disposals/" + D2 + "/confirm", Map.of(), admin).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> again = post("/api/disposals/" + D2 + "/confirm", Map.of(), admin);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().get("code").asText()).isEqualTo("INVALID_STATUS");
        assertThat(onHand(WATER)).isEqualTo(117);
        assertThat(put("/api/disposals/" + D2, Map.of("items", List.of(line(WATER, 1, "DAMAGED"))), user2)
                .getStatusCode()).as("đã 확정 thì không sửa được").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void twoManagersConfirmingAtTheSameTimeDeductOnlyOnce() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<HttpStatusCode> task = () -> {
            start.await();
            return post("/api/disposals/" + D1 + "/confirm", Map.of(), admin).getStatusCode();
        };
        var f1 = pool.submit(task);
        var f2 = pool.submit(task);
        start.countDown();
        List<HttpStatusCode> statuses = List.of(f1.get(), f2.get());
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        assertThat(onHand(KIMBAP)).isEqualTo(9);
        assertThat(get("/api/inventory/" + KIMBAP + "/transactions", user).getBody()).hasSize(1);
    }

    // ================= 확정취소 / 마감 =================

    @Test
    void cancelConfirmRestoresStockWithReversalEntryAndReturnsToRegistered() {
        post("/api/disposals/" + D1 + "/confirm", Map.of(), admin);
        assertThat(onHand(KIMBAP)).isEqualTo(9);

        assertThat(post("/api/disposals/" + D1 + "/cancel-confirm", Map.of(), admin).getStatusCode())
                .as("thiếu lý do").isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode d = post("/api/disposals/" + D1 + "/cancel-confirm", Map.of("reason", "Xác nhận nhầm"), admin).getBody();

        assertThat(d.get("statusCode").asText()).isEqualTo("10");
        assertThat(d.get("confirmCancelReason").asText()).isEqualTo("Xác nhận nhầm");
        assertThat(onHand(KIMBAP)).isEqualTo(12);
        JsonNode ledger = get("/api/inventory/" + KIMBAP + "/transactions", user).getBody();
        assertThat(ledger).hasSize(2); // không xóa dòng cũ: thêm dòng ngược dấu
        assertThat(ledger.get(0).get("txType").asText()).isEqualTo("DISPOSAL_CANCEL");
        assertThat(ledger.get(0).get("qtyChange").asLong()).isEqualTo(3);
        assertThat(ledger.get(0).get("costAmount").asLong()).isEqualTo(2_400);
    }

    @Test
    void closedBusinessDateBlocksCancelConfirmAndTodayClosingBlocksEverything() {
        ResponseEntity<JsonNode> res = post("/api/disposals/" + Y1 + "/cancel-confirm", Map.of("reason", "x"), admin);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().get("code").asText()).isEqualTo("CLOSED_PERIOD");

        assertThat(post("/api/store/closing", Map.of("closeDate", TODAY.toString()), user).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/api/store/closing", Map.of("closeDate", TODAY.plusDays(1).toString()), admin).getStatusCode())
                .as("không chốt ngày tương lai").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/api/store/closing", Map.of("closeDate", TODAY.toString()), admin).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(post("/api/disposals/" + D1 + "/confirm", Map.of(), admin).getBody().get("code").asText())
                .isEqualTo("CLOSED_PERIOD");
        assertThat(post("/api/disposals", Map.of("items", List.of(line(KIMBAP, 1, "EXPIRED"))), user)
                .getBody().get("code").asText()).isEqualTo("CLOSED_PERIOD");
    }

    @Test
    void reasonsAndItemLookupForRegistrationScreen() {
        JsonNode reasons = get("/api/disposals/reasons", user).getBody();
        assertThat(reasons).hasSize(6);
        assertThat(reasons.get(0).get("koreanName").asText()).isEqualTo("유통기한 경과");

        JsonNode item = get("/api/inventory/" + COFFEE, user).getBody();
        assertThat(item.get("availableQty").asLong()).isEqualTo(20);
        assertThat(item.get("costPrice").asLong()).isEqualTo(3_500);
        assertThat(get("/api/inventory/000", user).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/disposals", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ================= helpers =================

    private static String no(LocalDate date, int seq) {
        return String.format("S001-%s-%04d", date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE), seq);
    }

    private static Map<String, Object> line(String code, long qty, String reason) {
        return Map.of("itemCode", code, "qty", qty, "reasonCode", reason);
    }

    private long onHand(String itemCode) {
        return get("/api/inventory/" + itemCode, user).getBody().get("onHandQty").asLong();
    }

    private ResponseEntity<JsonNode> get(String url, String token) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> post(String url, Object body, String token) {
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> put(String url, Object body, String token) {
        return rest.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, headers(token)), JsonNode.class);
    }

    private static HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
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
