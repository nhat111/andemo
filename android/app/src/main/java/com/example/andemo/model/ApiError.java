package com.example.andemo.model;

import com.google.gson.Gson;

import okhttp3.ResponseBody;

/** Lỗi trả về từ backend: { code, message, remainingAttempts } */
public class ApiError {
    public static final String MENU_LOCKED = "MENU_LOCKED";
    public static final String PIN_NOT_SET = "PIN_NOT_SET";
    public static final String PIN_WRONG = "PIN_WRONG";
    public static final String OTP_REQUIRED = "OTP_REQUIRED";
    public static final String OTP_EXPIRED = "OTP_EXPIRED";
    public static final String OTP_ATTEMPTS_EXCEEDED = "OTP_ATTEMPTS_EXCEEDED";

    private String code;
    private String message;
    private Integer remainingAttempts;

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public Integer getRemainingAttempts() { return remainingAttempts; }

    /** Parse errorBody của Retrofit, lỗi parse → trả object rỗng với message chung */
    public static ApiError from(ResponseBody errorBody, int httpCode) {
        ApiError fallback = new ApiError();
        fallback.message = "Lỗi server: " + httpCode;
        if (errorBody == null) return fallback;
        try {
            ApiError parsed = new Gson().fromJson(errorBody.charStream(), ApiError.class);
            return parsed != null && parsed.message != null ? parsed : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}
