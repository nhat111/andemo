package com.example.andemo.disposal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 점포재고: tồn kho 1 mặt hàng tại cửa hàng (demo: 1 cửa hàng, khóa theo mã hàng).
 * Giá vốn / giá bán dùng để tính 폐기금액 (tiền hàng hủy) lúc đăng ký phiếu.
 */
@Entity
@Table(name = "inventory_item")
@Data
@NoArgsConstructor
public class InventoryItem {

    /** 상품코드 (thường là barcode) */
    @Id
    private String itemCode;

    private String itemName;

    /** 점포코드 */
    private String storeCode;

    /** 현재고: tồn thực tế */
    private long onHandQty;

    /** Đã giữ chỗ cho nghiệp vụ khác (반품 대기, 점간이동 출고 대기…): không được hủy phần này */
    private long allocatedQty;

    /** 원가 (giá vốn, KRW) */
    private long costPrice;

    /** 매가 (giá bán, KRW) */
    private long salePrice;

    /** Khóa lạc quan: phát hiện 2 nghiệp vụ cùng sửa tồn */
    @Version
    private long version;

    public InventoryItem(String itemCode, String itemName, String storeCode, long onHandQty, long allocatedQty,
                         long costPrice, long salePrice) {
        this.itemCode = itemCode;
        this.itemName = itemName;
        this.storeCode = storeCode;
        this.onHandQty = onHandQty;
        this.allocatedQty = allocatedQty;
        this.costPrice = costPrice;
        this.salePrice = salePrice;
    }

    /** 가용재고 = 현재고 − 할당 */
    public long getAvailableQty() {
        return onHandQty - allocatedQty;
    }
}
