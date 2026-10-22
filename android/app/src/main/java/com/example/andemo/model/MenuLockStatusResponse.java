package com.example.andemo.model;

import java.util.List;

public class MenuLockStatusResponse {
    private String storeId;
    private boolean pinSet;
    private boolean otpRequired;
    private List<MenuStatusDto> menus;

    public String getStoreId() { return storeId; }
    public boolean isPinSet() { return pinSet; }
    public boolean isOtpRequired() { return otpRequired; }
    public List<MenuStatusDto> getMenus() { return menus; }
}
