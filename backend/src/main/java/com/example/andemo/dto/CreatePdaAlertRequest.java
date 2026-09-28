package com.example.andemo.dto;

import lombok.Data;

@Data
public class CreatePdaAlertRequest {
    // null = gửi cho mọi PDA
    private String deviceId;
    private String storeCode;
    private String message;
    // Thời hạn hiệu lực của lệnh (giây). PDA offline lâu hơn thì không kêu nữa.
    private Long ttlSeconds;
}
