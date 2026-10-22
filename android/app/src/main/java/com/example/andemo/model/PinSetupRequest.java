package com.example.andemo.model;

public class PinSetupRequest {
    private String otp;
    private String newPin;
    private String confirmPin;

    public PinSetupRequest(String otp, String newPin, String confirmPin) {
        this.otp = otp;
        this.newPin = newPin;
        this.confirmPin = confirmPin;
    }

    public String getOtp() { return otp; }
    public String getNewPin() { return newPin; }
    public String getConfirmPin() { return confirmPin; }
}
