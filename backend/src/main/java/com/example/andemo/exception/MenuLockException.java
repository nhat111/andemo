package com.example.andemo.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ của chức năng khóa menu, trả về client dạng ApiError */
@Getter
public class MenuLockException extends RuntimeException {

    public static final String MENU_LOCKED = "MENU_LOCKED";
    public static final String MENU_NOT_FOUND = "MENU_NOT_FOUND";
    public static final String PIN_NOT_SET = "PIN_NOT_SET";
    public static final String PIN_INVALID_FORMAT = "PIN_INVALID_FORMAT";
    public static final String PIN_MISMATCH = "PIN_MISMATCH";
    public static final String PIN_WRONG = "PIN_WRONG";
    public static final String OTP_REQUIRED = "OTP_REQUIRED";
    public static final String OTP_INVALID = "OTP_INVALID";
    public static final String OTP_EXPIRED = "OTP_EXPIRED";
    public static final String OTP_ATTEMPTS_EXCEEDED = "OTP_ATTEMPTS_EXCEEDED";
    public static final String OTP_TOO_FREQUENT = "OTP_TOO_FREQUENT";

    private final HttpStatus status;
    private final String code;
    private final Integer remainingAttempts;

    public MenuLockException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public MenuLockException(HttpStatus status, String code, String message, Integer remainingAttempts) {
        super(message);
        this.status = status;
        this.code = code;
        this.remainingAttempts = remainingAttempts;
    }
}
