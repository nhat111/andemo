package com.example.andemo.model;

public class OtpVerifyRequest {
    private String otp;

    public OtpVerifyRequest(String otp) {
        this.otp = otp;
    }

    public String getOtp() { return otp; }
}
