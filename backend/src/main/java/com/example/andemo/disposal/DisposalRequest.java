package com.example.andemo.disposal;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Phiếu hủy hàng (header). Dòng chi tiết: {@link DisposalItem}. */
@Entity
@Table(name = "disposal_request")
@Data
@NoArgsConstructor
public class DisposalRequest {

    /** Ví dụ DSP-20260929-001 */
    @Id
    private String disposalNo;

    private String warehouseCode;

    @Enumerated(EnumType.STRING)
    private DisposalStatus status = DisposalStatus.REQUESTED;

    /** Lý do chung: hết hạn, hư hỏng, thu hồi… */
    private String reason;

    private String requestedBy;
    private Instant requestedAt;

    private String confirmedBy;
    private Instant confirmedAt;

    /**
     * Khóa lạc quan: app gửi kèm version đang hiển thị khi bấm Xác nhận. Người khác đã sửa / xác nhận
     * trước đó thì version khác → báo "dữ liệu đã thay đổi", tránh xác nhận trên dữ liệu cũ.
     */
    @Version
    private long version;

    @OneToMany(mappedBy = "disposal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<DisposalItem> items = new ArrayList<>();

    public DisposalRequest(String disposalNo, String warehouseCode, String reason, String requestedBy, Instant requestedAt) {
        this.disposalNo = disposalNo;
        this.warehouseCode = warehouseCode;
        this.reason = reason;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
    }

    public DisposalRequest addItem(String itemCode, long qty, String reasonCode) {
        DisposalItem item = new DisposalItem();
        item.setDisposal(this);
        item.setLineNo(items.size() + 1);
        item.setItemCode(itemCode);
        item.setQty(qty);
        item.setReasonCode(reasonCode);
        items.add(item);
        return this;
    }
}
