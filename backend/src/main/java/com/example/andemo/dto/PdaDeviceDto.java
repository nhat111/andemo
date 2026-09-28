package com.example.andemo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * PDA cho màn hình requester. secondsSinceLastSeen và online do server tính theo giờ server,
 * để client không phụ thuộc giờ trên máy (có thể sai).
 */
@Data
@AllArgsConstructor
public class PdaDeviceDto {
    private String deviceId;
    private String deviceName;
    private String username;
    private Instant lastSeenAt;
    private long secondsSinceLastSeen;
    // Đang giữ kết nối WebSocket, hoặc poll trong 90 giây gần nhất
    private boolean online;
    // Đang giữ kết nối WebSocket: lệnh được đẩy xuống ngay
    private boolean connected;
}
