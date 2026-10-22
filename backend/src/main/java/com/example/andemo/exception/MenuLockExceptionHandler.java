package com.example.andemo.exception;

import com.example.andemo.dto.ApiError;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MenuLockExceptionHandler {

    @ExceptionHandler(MenuLockException.class)
    public ResponseEntity<ApiError> handle(MenuLockException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new ApiError(e.getCode(), e.getMessage(), e.getRemainingAttempts()));
    }
}
