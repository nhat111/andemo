package com.example.andemo.disposal;

import org.springframework.http.HttpStatus;

import java.util.List;

/** Lỗi nghiệp vụ của phiếu hủy, controller đổi thành HTTP status + {code, message, details}. */
public class DisposalException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<String> details;

    public DisposalException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public DisposalException(HttpStatus status, String code, String message, List<String> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<String> getDetails() {
        return details;
    }
}
