package com.example.andemo.disposal;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 수불 (sổ nhập–xuất–tồn): mỗi lần tồn thay đổi ghi 1 dòng, không sửa / xóa.
 * Hủy xác nhận không xóa dòng cũ mà ghi thêm 1 dòng ngược dấu (역분개).
 */
@Entity
@Table(name = "inventory_transaction")
@Data
@NoArgsConstructor
public class InventoryTransaction {

    /** DISPOSAL: 폐기 (trừ tồn) */
    public static final String TYPE_DISPOSAL = "DISPOSAL";
    /** DISPOSAL_CANCEL: 폐기확정취소 (cộng lại tồn) */
    public static final String TYPE_DISPOSAL_CANCEL = "DISPOSAL_CANCEL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String storeCode;

    /** 영업일자 của chứng từ gốc */
    private LocalDate businessDate;

    private String itemCode;

    private String txType;

    /** Âm = trừ tồn, dương = cộng tồn */
    private long qtyChange;

    private long beforeQty;
    private long afterQty;

    /** 원가금액 của biến động (cùng dấu với số lượng) */
    private long costAmount;

    /** Chứng từ gốc: số phiếu hủy + dòng */
    private String refNo;
    private int refLineNo;

    private String createdBy;
    private Instant createdAt;
}
