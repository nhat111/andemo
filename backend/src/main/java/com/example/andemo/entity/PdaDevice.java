package com.example.andemo.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * PDA mà server biết. Cập nhật mỗi lần PDA liên lạc (poll pending, kết nối / ngắt WebSocket).
 * Requirement §3.5 "bảng quản lý device" (userId, deviceId, storeCode, updatedAt).
 */
@Entity
@Table(name = "pda_device")
@Data
@NoArgsConstructor
public class PdaDevice {

    @Id
    private String deviceId;

    // Ví dụ "realme RMX1851"
    private String deviceName;

    // User đang login trên PDA (theo token của lần liên lạc cuối)
    private String username;

    private Instant lastSeenAt;
}
