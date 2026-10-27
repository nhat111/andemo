package com.example.andemo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MenuLockStatusResponse {
    private String storeId;
    private String storeName;
    private String ownerPhone;     // SĐT chủ cửa hàng đã che (010-****-8110) – nhận OTP
    private boolean pinSet;        // Đã cài mật khẩu chưa?
    private boolean otpRequired;   // sai PIN quá N lần → phải xác thực OTP
    private List<MenuStatusDto> menus;
}
