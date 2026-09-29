package com.example.andemo.disposal;

import java.time.Instant;
import java.util.List;

/** Dữ liệu trả cho app / web (không trả thẳng entity để không lộ quan hệ JPA và kiểm soát được field). */
public final class DisposalDtos {

    private DisposalDtos() {
    }

    /** 1 dòng trên màn "Disposal Inquiry" */
    public record Summary(String disposalNo, String warehouseCode, DisposalStatus status, String reason,
                          String requestedBy, Instant requestedAt, int lineCount, long totalQty) {
    }

    /** Màn "Disposal Detail View" */
    public record Detail(String disposalNo, String warehouseCode, DisposalStatus status, String reason,
                         String requestedBy, Instant requestedAt, String confirmedBy, Instant confirmedAt,
                         long version,
                         /** true = nút Xác nhận được bật */
                         boolean confirmable,
                         /** Lý do không xác nhận được (trạng thái, thiếu tồn…), để app hiển thị trước khi bấm */
                         List<String> issues,
                         List<Line> items) {
    }

    public record Line(int lineNo, String itemCode, String itemName, long qty, String reasonCode,
                       Long onHandQty, Long availableQty,
                       /** Đủ số lượng khả dụng để hủy (chỉ có ý nghĩa khi phiếu còn REQUESTED) */
                       boolean sufficient) {
    }

    /** Body của POST /confirm: version đang hiển thị trên màn chi tiết */
    public record ConfirmRequest(Long version) {
    }

    /** Lỗi nghiệp vụ trả về cho app */
    public record Error(String code, String message, List<String> details) {
    }
}
