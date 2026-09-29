package com.example.andemo.disposal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tồn kho của 1 mặt hàng tại 1 kho (demo: 1 kho).
 * Số lượng khả dụng = tồn thực tế − đã giữ chỗ (cho đơn khác đang xử lý).
 */
@Entity
@Table(name = "inventory_item")
@Data
@NoArgsConstructor
public class InventoryItem {

    @Id
    private String itemCode;

    private String itemName;

    private String warehouseCode;

    /** Tồn thực tế trong kho */
    private long onHandQty;

    /** Đã giữ chỗ cho nghiệp vụ khác (xuất hàng, chuyển kho…): không được hủy phần này */
    private long allocatedQty;

    /** Khóa lạc quan: phát hiện 2 người cùng sửa tồn kho (Task 18 "Inventory Quantity Synchronization") */
    @Version
    private long version;

    public InventoryItem(String itemCode, String itemName, String warehouseCode, long onHandQty, long allocatedQty) {
        this.itemCode = itemCode;
        this.itemName = itemName;
        this.warehouseCode = warehouseCode;
        this.onHandQty = onHandQty;
        this.allocatedQty = allocatedQty;
    }

    public long getAvailableQty() {
        return onHandQty - allocatedQty;
    }
}
