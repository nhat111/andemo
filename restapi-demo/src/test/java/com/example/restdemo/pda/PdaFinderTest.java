package com.example.restdemo.pda;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 6 trường hợp khách đưa ra + các điểm kỹ thuật (interceptor, đăng xuất, chặn ghi dồn dập).
 * Thời điểm "đã dùng cách đây N phút" được ghi thẳng vào bảng (seed) để test không phải chờ thật.
 */
@SpringBootTest(properties = {
        "pda.activity.min-interval-ms=0",                                           // test: không chặn 1 phút
        "spring.datasource.url=jdbc:h2:mem:pdatest;MODE=Oracle;DB_CLOSE_DELAY=-1"  // DB riêng cho class test này
})
@AutoConfigureMockMvc
class PdaFinderTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PdaFinderService finder;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM PDA_DEVICE_ACTIVITY");
    }

    // ---------------- interceptor ----------------

    @Test
    void interceptorRecordsOnlySuccessfulCallsFromLoggedInPda() throws Exception {
        pdaCall("A", "S001", "001");                                          // PDA gọi API thành công
        mvc.perform(get("/api/products/999").header("X-Unique-Id", "B")       // API lỗi 404
                .header("X-Store-Cd", "S001").header("X-User-Id", "001")).andExpect(status().isNotFound());
        mvc.perform(get("/api/products").header("X-Store-Cd", "S001")         // PC: không có X-Unique-Id
                .header("X-User-Id", "001")).andExpect(status().isOk());
        mvc.perform(get("/api/products").header("X-Unique-Id", "C"))          // chưa đăng nhập
                .andExpect(status().isOk());

        assertThat(jdbc.queryForList("SELECT UNIQUE_ID FROM PDA_DEVICE_ACTIVITY", String.class))
                .containsExactly("A");
    }

    @Test
    void logoutKeepsDeviceFindableAndNextUseClearsLogout() throws Exception {
        mvc.perform(post("/api/auth/login").header("X-Unique-Id", "A")
                .header("X-Store-Cd", "S001").header("X-User-Id", "001")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("X-Unique-Id", "A")
                .header("X-Store-Cd", "S001").header("X-User-Id", "001")).andExpect(status().isOk());

        find("S001", "001")                                                   // đã đăng xuất vẫn tìm được
                .andExpect(jsonPath("$.rule").value("USER"))
                .andExpect(jsonPath("$.targets[*].uniqueId", contains("A")))
                .andExpect(jsonPath("$.targets[0].loggedOut").value(true));

        pdaCall("A", "S001", "002");                                          // ca sau đăng nhập dùng tiếp
        find("S001", "002").andExpect(jsonPath("$.targets[0].loggedOut").value(false));
    }

    @Test
    void pcWithoutLoginGets401() throws Exception {
        mvc.perform(post("/api/pda/find")).andExpect(status().isUnauthorized());
    }

    // ---------------- 6 trường hợp của khách ----------------

    /** TH1: thay thiết bị A → B, vẫn người dùng 001 → gửi tới B */
    @Test
    void case1_replaceDevice() throws Exception {
        seed("A", "S001", "001", 120);
        pdaCall("B", "S001", "001");

        find("S001", "001")
                .andExpect(jsonPath("$.rule").value("USER"))
                .andExpect(jsonPath("$.targets[*].uniqueId", contains("B")));
    }

    /** TH2: máy A cố định, người dùng 001 → 002 */
    @Test
    void case2_handOverDevice() throws Exception {
        seed("A", "S001", "001", 60);
        pdaCall("A", "S001", "002");                                          // A giờ thuộc 002

        find("S001", "002")
                .andExpect(jsonPath("$.rule").value("USER"))
                .andExpect(jsonPath("$.targets[*].uniqueId", contains("A")));
        // 001 không còn máy: (b) mặc định → máy gần nhất của cửa hàng
        find("S001", "001")
                .andExpect(jsonPath("$.rule").value("STORE"))
                .andExpect(jsonPath("$.targets[*].uniqueId", contains("A")));
        // (a) fallback-to-store = false → báo không có PDA
        PdaFinderService.Result strict = finder.find(new LoginUser("S001", "001"), false);
        assertThat(strict.getRule()).isEqualTo("NONE");
        assertThat(strict.getTargets()).isEmpty();
    }

    /** TH3: thu hồi A về trụ sở */
    @Test
    void case3_recallToHeadOffice() throws Exception {
        seed("A", "S001", "001", 30);
        pdaCall("A", "HQ", "hq01");                                           // đăng nhập ở trụ sở

        find("S001", "001")
                .andExpect(jsonPath("$.rule").value("NONE"))
                .andExpect(jsonPath("$.targets", empty()));
    }

    /** TH3 (thu hồi nhưng chỉ tắt máy cất đi): sau N ngày không dùng thì tự loại */
    @Test
    void case3_recalledAndPoweredOffIsDroppedAfterInactiveDays() throws Exception {
        seed("A", "S001", "001", 8 * 24 * 60);                                // 8 ngày trước
        find("S001", "001").andExpect(jsonPath("$.rule").value("NONE"));

        seed("A", "S001", "001", 6 * 24 * 60);                                // 6 ngày trước: còn trong 7 ngày
        find("S001", "001").andExpect(jsonPath("$.targets[*].uniqueId", contains("A")));
    }

    /** TH4: 001 dùng đồng thời A và B → gửi cả 2 (cách nhau <= 10 phút); C dùng từ lâu thì không */
    @Test
    void case4_sameUserSeveralDevices() throws Exception {
        seed("A", "S001", "001", 3);
        seed("B", "S001", "001", 8);
        seed("C", "S001", "001", 30);

        find("S001", "001")
                .andExpect(jsonPath("$.rule").value("USER"))
                .andExpect(jsonPath("$.targets[*].uniqueId", contains("A", "B")));
    }

    /** TH5: thu hồi A, cấp lại cho 002 (cửa hàng khác) */
    @Test
    void case5_reassignToAnotherUser() throws Exception {
        seed("A", "S001", "001", 60 * 24);
        pdaCall("A", "S002", "002");

        find("S001", "001").andExpect(jsonPath("$.rule").value("NONE"));
        find("S002", "002")
                .andExpect(jsonPath("$.rule").value("USER"))
                .andExpect(jsonPath("$.targets[*].uniqueId", contains("A")));
    }

    /** TH6: máy A dùng chung giữa part-time (P01) và chủ cửa hàng (OWN) */
    @Test
    void case6_sharedDeviceOwnerAndPartTimer() throws Exception {
        seed("A", "S001", "OWN", 300);
        pdaCall("A", "S001", "P01");                                          // part-time dùng A sau cùng

        // Chủ bấm: không còn máy nào gắn với OWN → máy gần nhất của cửa hàng = A
        find("S001", "OWN")
                .andExpect(jsonPath("$.rule").value("STORE"))
                .andExpect(jsonPath("$.targets[*].uniqueId", contains("A")));
    }

    /** TH6 (lưu ý): chủ có máy riêng B dùng từ hôm qua → ưu tiên B, KHÔNG phải A part-time vừa dùng */
    @Test
    void case6_ownerWithOwnDeviceGetsOwnDevice() throws Exception {
        seed("B", "S001", "OWN", 60 * 24);
        pdaCall("A", "S001", "P01");

        find("S001", "OWN")
                .andExpect(jsonPath("$.rule").value("USER"))
                .andExpect(jsonPath("$.targets[*].uniqueId", contains("B")));
    }

    /** Máy của cửa hàng khác không bao giờ bị chọn */
    @Test
    void otherStoreDevicesAreNeverChosen() throws Exception {
        seed("X", "S999", "001", 1);
        find("S001", "001").andExpect(jsonPath("$.rule").value("NONE"));
    }

    /** Nhiều máy của cửa hàng, người bấm không có máy → các máy gần nhất của cửa hàng (<= 10 phút) */
    @Test
    void storeFallbackAlsoSendsToNearDevices() throws Exception {
        seed("A", "S001", "P01", 2);
        seed("B", "S001", "P02", 6);
        seed("C", "S001", "P03", 60);

        find("S001", "OWN")
                .andExpect(jsonPath("$.rule").value("STORE"))
                .andExpect(jsonPath("$.targets[*].uniqueId", containsInAnyOrder("A", "B")));
    }

    // ---------------- chặn ghi dồn dập (không cần Spring) ----------------

    @Test
    void touchWritesAtMostOncePerIntervalPerDeviceAndUser() {
        List<String> writes = new ArrayList<>();
        DeviceActivityMapper counting = new DeviceActivityMapper() {
            public int touch(String uniqueId, String storeCd, String userId) {
                writes.add(uniqueId + "/" + userId);
                return 1;
            }

            public int logout(String uniqueId) {
                return 1;
            }

            public List<DeviceActivity> findCandidates(String s, String u, LocalDateTime t) {
                return new ArrayList<>();
            }

            public DeviceActivity findByUniqueId(String uniqueId) {
                return null;
            }
        };
        DeviceActivityService service = new DeviceActivityService(counting, 60_000);

        service.touch("A", new LoginUser("S001", "001"));
        service.touch("A", new LoginUser("S001", "001"));  // trong 1 phút: bỏ qua
        service.touch("A", new LoginUser("S001", "002"));  // đổi người dùng: ghi ngay
        service.logout("A");
        service.touch("A", new LoginUser("S001", "002"));  // sau đăng xuất: ghi ngay

        assertThat(writes).containsExactly("A/001", "A/002", "A/002");
    }

    // ---------------- tiện ích ----------------

    /** PDA gọi 1 API bất kỳ (interceptor ghi nhận) */
    private void pdaCall(String uniqueId, String storeCd, String userId) throws Exception {
        mvc.perform(get("/api/products").header("X-Unique-Id", uniqueId)
                .header("X-Store-Cd", storeCd).header("X-User-Id", userId))
                .andExpect(status().isOk());
    }

    /** PC bấm "Tìm PDA" */
    private ResultActions find(String storeCd, String userId) throws Exception {
        return mvc.perform(post("/api/pda/find").header("X-Store-Cd", storeCd).header("X-User-Id", userId))
                .andExpect(status().isOk());
    }

    /** Ghi thẳng 1 máy "đã dùng cách đây minutesAgo phút" */
    private void seed(String uniqueId, String storeCd, String userId, long minutesAgo) {
        jdbc.update("DELETE FROM PDA_DEVICE_ACTIVITY WHERE UNIQUE_ID = ?", uniqueId);
        jdbc.update("INSERT INTO PDA_DEVICE_ACTIVITY (UNIQUE_ID, STORE_CD, USER_ID, LAST_ACTIVE_AT) VALUES (?, ?, ?, ?)",
                uniqueId, storeCd, userId, Timestamp.valueOf(LocalDateTime.now().minusMinutes(minutesAgo)));
    }
}
