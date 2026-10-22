package com.example.andemo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MenuLockStatusResponse {
    private String storeId;
    private boolean pinSet;        // 비번 설정 ? (Đã cài mật khẩu?)
    private boolean otpRequired;   // sai PIN quá N lần → phải xác thực OTP
    private List<MenuStatusDto> menus;
}
