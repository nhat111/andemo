package com.example.andemo.disposal;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** 1 dòng của phiếu hủy: mặt hàng + số lượng hủy. */
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

    private long qty;

    /** EXPIRED / DAMAGED / RECALL … */
    private String reasonCode;
}
