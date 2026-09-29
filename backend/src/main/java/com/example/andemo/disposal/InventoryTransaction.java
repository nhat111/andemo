package com.example.andemo.disposal;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Lịch sử biến động tồn kho (Task 18 "Inventory Transaction History Update").
 * Mỗi lần trừ / cộng tồn ghi 1 dòng, không sửa không xóa: truy vết được tồn kho thay đổi vì đâu.
 */
@Entity
@Table(name = "inventory_transaction")
@Data
@NoArgsConstructor
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String itemCode;

    /** DISPOSAL (hủy hàng); sau này: RECEIPT, ISSUE, ADJUST… */
    private String txType;

    /** Âm = trừ tồn */
    private long qtyChange;

    private long beforeQty;
    private long afterQty;

    /** Chứng từ gốc, ví dụ số phiếu hủy */
    private String refNo;
    private int refLineNo;

    private String createdBy;
    private Instant createdAt;
}
