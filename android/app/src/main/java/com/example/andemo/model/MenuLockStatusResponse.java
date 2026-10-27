package com.example.andemo.model;

import java.util.List;

public class MenuLockStatusResponse {
    private String storeId;
    private String storeName;
    private String ownerPhone; // SĐT chủ cửa hàng đã che, nhận OTP
    private boolean pinSet;
    private boolean otpRequired;
    private List<MenuStatusDto> menus;

    public String getStoreId() { return storeId; }
    public String getStoreName() { return storeName; }
    public String getOwnerPhone() { return ownerPhone; }
    public boolean isPinSet() { return pinSet; }
    public boolean isOtpRequired() { return otpRequired; }
    public List<MenuStatusDto> getMenus() { return menus; }

    /** Đang khóa chức năng = có ít nhất 1 menu đang khóa → nút trên title hiện "Mở khóa" */
    public boolean isAnyLocked() {
        if (menus == null) return false;
        for (MenuStatusDto m : menus) {
            if (m.isLocked()) return true;
        }
        return false;
    }
}
