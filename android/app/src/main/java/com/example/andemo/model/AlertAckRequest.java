package com.example.andemo.model;

public class AlertAckRequest {
    private final String deviceId;
    private final String status;

    public AlertAckRequest(String deviceId, String status) {
        this.deviceId = deviceId;
        this.status = status;
    }

    public String getDeviceId() { return deviceId; }
    public String getStatus() { return status; }
}
