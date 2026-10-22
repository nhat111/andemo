package com.example.andemo.dto;

import lombok.Data;

@Data
public class PinSetupRequest {
    private String otp;
    private String newPin;
    private String confirmPin;
}
