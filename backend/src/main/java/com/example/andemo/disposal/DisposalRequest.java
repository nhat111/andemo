package com.example.andemo.disposal;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 폐기전표 (phiếu hủy hàng), header. Dòng chi tiết: {@link DisposalItem}. */
@Entity
@Table(name = "disposal_request")
@Data
@NoArgsConstructor
public class DisposalRequest {

    /** 전표번호: 점포코드-영업일자-순번, ví dụ S001-20260929-0001 */
    @Id
    private String disposalNo;

    private String storeCode;

    /** 영업일자: ngày kinh doanh phiếu thuộc về (tồn và 수불 ghi theo ngày này) */
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    private DisposalStatus status = DisposalStatus.REGISTERED;

    /** 비고 */
    private String remark;

    private String registeredBy;
    private Instant registeredAt;
    private String updatedBy;
    private Instant updatedAt;
    private String confirmedBy;
    private Instant confirmedAt;
    private String cancelledBy;
    private Instant cancelledAt;
    private String cancelReason;

    /** Lần 확정취소 gần nhất (phiếu quay về 등록) */
    private String confirmCancelledBy;
    private Instant confirmCancelledAt;
    private String confirmCancelReason;

    /** Khóa lạc quan: app gửi kèm version đang hiển thị; khác thì báo dữ liệu đã thay đổi */
    @Version
    private long version;

    @OneToMany(mappedBy = "disposal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<DisposalItem> items = new ArrayList<>();

    public DisposalItem addItem(InventoryItem item, long qty, DisposalReason reason) {
        DisposalItem line = new DisposalItem();
        line.setDisposal(this);
        line.setLineNo(items.size() + 1);
        line.setItemCode(item.getItemCode());
        line.setItemName(item.getItemName());
        line.setQty(qty);
        line.setReasonCode(reason);
        // Chụp giá tại thời điểm đăng ký: đổi giá sau này không làm đổi tiền của phiếu cũ
        line.setCostPrice(item.getCostPrice());
        line.setSalePrice(item.getSalePrice());
        items.add(line);
        return line;
    }

    public long getTotalQty() {
        return items.stream().mapToLong(DisposalItem::getQty).sum();
    }

    public long getTotalCostAmount() {
        return items.stream().mapToLong(DisposalItem::getCostAmount).sum();
    }

    public long getTotalSaleAmount() {
        return items.stream().mapToLong(DisposalItem::getSaleAmount).sum();
    }
}
