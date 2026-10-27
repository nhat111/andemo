package com.example.andemo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MenuLockFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private String token;

    @BeforeEach
    void login() throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = mapper.readTree(body).get("token").asText();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req) {
        return req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private void setupPin(String pin) throws Exception {
        mvc.perform(auth(post("/api/menu-lock/otp/request"))).andExpect(status().isOk());
        mvc.perform(auth(post("/api/menu-lock/pin"))
                        .content("{\"otp\":\"123456\",\"newPin\":\"" + pin + "\",\"confirmPin\":\"" + pin + "\"}"))
                .andExpect(status().isOk());
    }

    private void lock(String... codes) throws Exception {
        String json = mapper.writeValueAsString(java.util.Map.of("menuCodes", java.util.List.of(codes)));
        mvc.perform(auth(post("/api/menu-lock/lock")).content(json)).andExpect(status().isOk());
    }

    @Test
    void lockRequiresPinFirst() throws Exception {
        mvc.perform(auth(get("/api/menu-lock/status")))
                .andExpect(jsonPath("$.pinSet").value(false))
                .andExpect(jsonPath("$.menus.length()").value(5));

        mvc.perform(auth(post("/api/menu-lock/lock")).content("{\"menuCodes\":[\"ORDER\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PIN_NOT_SET"));
    }

    @Test
    void pinSetupValidatesInput() throws Exception {
        mvc.perform(auth(post("/api/menu-lock/pin"))
                        .content("{\"otp\":\"123456\",\"newPin\":\"12a4\",\"confirmPin\":\"12a4\"}"))
                .andExpect(jsonPath("$.code").value("PIN_INVALID_FORMAT"));
        mvc.perform(auth(post("/api/menu-lock/pin"))
                        .content("{\"otp\":\"123456\",\"newPin\":\"1234\",\"confirmPin\":\"4321\"}"))
                .andExpect(jsonPath("$.code").value("PIN_MISMATCH"));
        mvc.perform(auth(post("/api/menu-lock/pin"))
                        .content("{\"otp\":\"000000\",\"newPin\":\"1234\",\"confirmPin\":\"1234\"}"))
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));
    }

    @Test
    void blockedMenuIsDeniedUntilUnlocked() throws Exception {
        setupPin("1234");
        lock("ORDER");

        mvc.perform(auth(get("/api/menu-lock/access/ORDER")))
                .andExpect(jsonPath("$.allowed").value(false));
        mvc.perform(auth(get("/api/store-menus/ORDER/data")))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("MENU_LOCKED"));
        // Menu khác không bị ảnh hưởng
        mvc.perform(auth(get("/api/store-menus/RETURN/data"))).andExpect(status().isOk());

        mvc.perform(auth(post("/api/menu-lock/unlock")).content("{\"menuCode\":\"ORDER\",\"pin\":\"1234\"}"))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/store-menus/ORDER/data"))).andExpect(status().isOk());

        mvc.perform(auth(get("/api/menu-lock/logs")))
                .andExpect(jsonPath("$[0].actionType").value("UNLOCK"))
                .andExpect(jsonPath("$[1].actionType").value("LOCK"))
                .andExpect(jsonPath("$[2].actionType").value("PIN_SET"));
    }

    @Test
    void costLockMasksSensitiveFields() throws Exception {
        setupPin("1234");
        mvc.perform(auth(get("/api/products/barcode/8901234567890")))
                .andExpect(jsonPath("$.costPrice").value(4200));

        lock("COST");

        mvc.perform(auth(get("/api/menu-lock/access/COST")))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.masked").value(true));
        mvc.perform(auth(get("/api/products/barcode/8901234567890")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salePrice").value(6000))
                .andExpect(jsonPath("$.costPrice").doesNotExist())
                .andExpect(jsonPath("$.marginRate").doesNotExist());
        mvc.perform(auth(get("/api/store-menus/COST/data")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masked").value(true))
                .andExpect(jsonPath("$.rows[0][2]").value(""));
    }

    @Test
    void wrongPinFiveTimesRequiresOtpReset() throws Exception {
        setupPin("1234");
        lock("INVENTORY");

        for (int i = 1; i <= 4; i++) {
            mvc.perform(auth(post("/api/menu-lock/unlock")).content("{\"menuCode\":\"INVENTORY\",\"pin\":\"0000\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.remainingAttempts").value(5 - i));
        }
        mvc.perform(auth(post("/api/menu-lock/unlock")).content("{\"menuCode\":\"INVENTORY\",\"pin\":\"0000\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("OTP_REQUIRED"));

        // Đúng PIN cũng không mở được khi đang bắt OTP
        mvc.perform(auth(post("/api/menu-lock/unlock")).content("{\"menuCode\":\"INVENTORY\",\"pin\":\"1234\"}"))
                .andExpect(jsonPath("$.code").value("OTP_REQUIRED"));

        JsonNode logs = mapper.readTree(mvc.perform(auth(get("/api/menu-lock/logs")))
                .andReturn().getResponse().getContentAsString());
        long failed = 0;
        for (JsonNode l : logs) if ("FAILED_TRY".equals(l.get("actionType").asText())) failed++;
        org.junit.jupiter.api.Assertions.assertEquals(5, failed);

        // Reset PIN qua OTP → mở khóa bằng PIN mới
        setupPin("5678");
        mvc.perform(auth(post("/api/menu-lock/unlock")).content("{\"menuCode\":\"INVENTORY\",\"pin\":\"5678\"}"))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/menu-lock/status")))
                .andExpect(jsonPath("$.otpRequired").value(false));
    }

    @Test
    void unlockWithoutMenuCodeUnlocksAll() throws Exception {
        setupPin("1234");
        lock("ORDER", "RETURN", "COST");

        mvc.perform(auth(post("/api/menu-lock/unlock")).content("{\"pin\":\"0000\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(auth(post("/api/menu-lock/unlock")).content("{\"pin\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.locked == true)]").isEmpty());
        mvc.perform(auth(get("/api/store-menus/ORDER/data"))).andExpect(status().isOk());
    }

    @Test
    void otpVerifyDoesNotConsumeOtp() throws Exception {
        mvc.perform(auth(post("/api/menu-lock/otp/request"))).andExpect(status().isOk());
        mvc.perform(auth(post("/api/menu-lock/otp/verify")).content("{\"otp\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));
        mvc.perform(auth(post("/api/menu-lock/otp/verify")).content("{\"otp\":\"123456\"}"))
                .andExpect(status().isOk());
        // OTP vẫn dùng được để lưu mật khẩu
        mvc.perform(auth(post("/api/menu-lock/pin"))
                        .content("{\"otp\":\"123456\",\"newPin\":\"1234\",\"confirmPin\":\"1234\"}"))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/menu-lock/status")))
                .andExpect(jsonPath("$.pinSet").value(true))
                .andExpect(jsonPath("$.ownerPhone").value("010-****-8110"));
    }
}
