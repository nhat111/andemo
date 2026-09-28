package com.example.andemo.model;

public class FcmTokenRequest {
    private final String fcmToken;
    private final String deviceName;

    public FcmTokenRequest(String fcmToken, String deviceName) {
        this.fcmToken = fcmToken;
        this.deviceName = deviceName;
    }

    public String getFcmToken() { return fcmToken; }
    public String getDeviceName() { return deviceName; }
}
