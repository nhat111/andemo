package com.example.andemo;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Giới hạn nhập sai OTP + thời gian chờ giữa 2 lần yêu cầu mã */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class OtpLimitTest {

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

    private MockHttpServletRequestBuilder verify(String otp) {
        return auth(post("/api/menu-lock/otp/verify")).content("{\"otp\":\"" + otp + "\"}");
    }

    @Test
    void otpIsCancelledAfterFiveWrongAttempts() throws Exception {
        mvc.perform(auth(post("/api/menu-lock/otp/request"))).andExpect(status().isOk());

        for (int i = 1; i <= 4; i++) {
            mvc.perform(verify("000000"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("OTP_INVALID"))
                    .andExpect(jsonPath("$.remainingAttempts").value(5 - i));
        }
        mvc.perform(verify("000000"))
                .andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));

        // Mã đã bị hủy → mã đúng cũng không dùng được, không đặt được PIN
        mvc.perform(verify("123456")).andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
        mvc.perform(auth(post("/api/menu-lock/pin"))
                        .content("{\"otp\":\"123456\",\"newPin\":\"1234\",\"confirmPin\":\"1234\"}"))
                .andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
    }

    @Test
    void wrongAttemptsAtSetupPinAlsoCount() throws Exception {
        mvc.perform(auth(post("/api/menu-lock/otp/request"))).andExpect(status().isOk());
        for (int i = 1; i <= 4; i++) {
            mvc.perform(verify("000000")).andExpect(jsonPath("$.code").value("OTP_INVALID"));
        }
        mvc.perform(auth(post("/api/menu-lock/pin"))
                        .content("{\"otp\":\"000000\",\"newPin\":\"1234\",\"confirmPin\":\"1234\"}"))
                .andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
    }

    @Test
    void otpCannotBeRequestedAgainWithinCooldown() throws Exception {
        mvc.perform(auth(post("/api/menu-lock/otp/request"))).andExpect(status().isOk());
        mvc.perform(auth(post("/api/menu-lock/otp/request")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_TOO_FREQUENT"));
        // Mã cũ vẫn dùng được
        mvc.perform(verify("123456")).andExpect(status().isOk());
    }
}
