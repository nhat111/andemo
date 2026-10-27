package com.example.andemo.service;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Thông tin cửa hàng hiển thị ở popup "Yêu cầu mã xác thực" (mã cửa hàng / tên cửa hàng / SĐT).
 * Số điện thoại = SĐT chủ cửa hàng trên hệ thống quản lý cửa hàng.
 * Demo: dữ liệu cứng. TODO production: lấy từ hệ thống quản lý cửa hàng.
 */
@Service
public class StoreInfoService {

    private record StoreInfo(String name, String ownerPhone) {}

    private static final Map<String, StoreInfo> STORES = Map.of(
            "STORE001", new StoreInfo("Cửa hàng Trung tâm", "01012348110"),
            "STORE002", new StoreInfo("Cửa hàng số 2", "01098765432")
    );

    public String storeName(String storeId) {
        StoreInfo info = STORES.get(storeId);
        return info != null ? info.name() : storeId;
    }

    /** 010-****-8110 */
    public String maskedOwnerPhone(String storeId) {
        StoreInfo info = STORES.get(storeId);
        if (info == null) return null;
        String p = info.ownerPhone();
        return p.substring(0, 3) + "-****-" + p.substring(p.length() - 4);
    }
}
