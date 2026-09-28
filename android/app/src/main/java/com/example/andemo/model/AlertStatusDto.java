package com.example.andemo.model;

/**
 * Trạng thái một lệnh tìm PDA, cho màn hình requester theo dõi.
 * status: SENT | DELIVERED | STOPPED_BY_USER | TIMED_OUT
 * expired: server tính theo giờ của server (lệnh SENT mà expired = PDA không nhận được kịp).
 */
public class AlertStatusDto {
    private String requestId;
    private String deviceId;
    private String message;
    private String status;
    private boolean expired;

    public String getRequestId() { return requestId; }
    public String getDeviceId() { return deviceId; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public boolean isExpired() { return expired; }
}
