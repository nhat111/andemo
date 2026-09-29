package com.example.andemo.disposal;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** 1 dòng của phiếu hủy. */
@Entity
@Table(name = "disposal_item")
@Data
@NoArgsConstructor
public class DisposalItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disposal_no")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private DisposalRequest disposal;

    private int lineNo;

    private String itemCode;

    /** Tên hàng lúc đăng ký (giữ nguyên dù master đổi tên) */
    private String itemName;

    private long qty;

    /** 폐기사유 */
    @Enumerated(EnumType.STRING)
    private DisposalReason reasonCode;

    /** 원가 / 매가 lúc đăng ký */
    private long costPrice;
    private long salePrice;

    /** 원가금액 = số lượng × giá vốn: khoản lỗ do hủy hàng */
    public long getCostAmount() {
        return qty * costPrice;
    }

    /** 매가금액 = số lượng × giá bán: doanh thu bị mất */
    public long getSaleAmount() {
        return qty * salePrice;
    }
}
