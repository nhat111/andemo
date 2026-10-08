package com.example.restdemo.pda;

/** Người dùng đang đăng nhập: cửa hàng + mã người dùng */
public class LoginUser {

    private final String storeCd;
    private final String userId;

    public LoginUser(String storeCd, String userId) {
        this.storeCd = storeCd;
        this.userId = userId;
    }

    public String getStoreCd() {
        return storeCd;
    }

    public String getUserId() {
        return userId;
    }
}
