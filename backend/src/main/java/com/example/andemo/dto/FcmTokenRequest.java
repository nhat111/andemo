package com.example.andemo.dto;

import lombok.Data;

@Data
public class FcmTokenRequest {
    // null / rỗng = hủy đăng ký
    private String fcmToken;
    private String deviceName;
}
