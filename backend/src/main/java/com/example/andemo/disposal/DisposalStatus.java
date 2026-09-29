package com.example.andemo.disposal;

/**
 * Trạng thái phiếu hủy (scrap). Task 17 "Disposal Status Validation": chỉ REQUESTED mới xác nhận được.
 *
 * REQUESTED ──confirm──► CONFIRMED (đã trừ tồn kho, không sửa / hủy được nữa)
 *     └──────cancel───► CANCELLED
 */
public enum DisposalStatus {
    REQUESTED,
    CONFIRMED,
    CANCELLED
}
