package com.example.andemo.model;

/**
 * PDA mà server biết (qua các lần poll). Server tự tính secondsSinceLastSeen và online
 * theo giờ của server, để app không phụ thuộc giờ trên máy (có thể sai).
 */
public class PdaDeviceDto {
    private String deviceId;
    private String deviceName;
    private long secondsSinceLastSeen;
    private boolean online;

    public String getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public long getSecondsSinceLastSeen() { return secondsSinceLastSeen; }
    public boolean isOnline() { return online; }
}
