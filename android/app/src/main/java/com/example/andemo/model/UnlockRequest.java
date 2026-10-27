package com.example.andemo.model;

public class UnlockRequest {
    private String menuCode; // null → mở khóa tất cả
    private String pin;

    public UnlockRequest(String menuCode, String pin) {
        this.menuCode = menuCode;
        this.pin = pin;
    }

    public String getMenuCode() { return menuCode; }
    public String getPin() { return pin; }
}
