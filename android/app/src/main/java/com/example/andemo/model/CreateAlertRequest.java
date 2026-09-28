package com.example.andemo.model;

public class CreateAlertRequest {
    // null = gửi cho mọi PDA
    private final String deviceId;
    private final String message;
    private final Long ttlSeconds;

    public CreateAlertRequest(String deviceId, String message, Long ttlSeconds) {
        this.deviceId = deviceId;
        this.message = message;
        this.ttlSeconds = ttlSeconds;
    }

    public String getDeviceId() { return deviceId; }
    public String getMessage() { return message; }
    public Long getTtlSeconds() { return ttlSeconds; }
}
