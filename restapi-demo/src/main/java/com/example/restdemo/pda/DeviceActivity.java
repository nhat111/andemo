package com.example.restdemo.pda;

import java.time.LocalDateTime;

/** 1 dòng của PDA_DEVICE_ACTIVITY (MyBatis tự map LAST_ACTIVE_AT → lastActiveAt) */
public class DeviceActivity {

    private String uniqueId;
    private String storeCd;
    private String userId;
    private LocalDateTime lastActiveAt;
    private LocalDateTime logoutAt;

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public String getStoreCd() {
        return storeCd;
    }

    public void setStoreCd(String storeCd) {
        this.storeCd = storeCd;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public LocalDateTime getLogoutAt() {
        return logoutAt;
    }

    public void setLogoutAt(LocalDateTime logoutAt) {
        this.logoutAt = logoutAt;
    }

    /** Đã đăng xuất sau lần hoạt động cuối (chỉ để PC hiển thị) */
    public boolean isLoggedOut() {
        return logoutAt != null && !logoutAt.isBefore(lastActiveAt);
    }
}
