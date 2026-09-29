package com.example.andemo.disposal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Dữ liệu vào / ra của API phiếu hủy (không trả thẳng entity JPA). */
public final class DisposalDtos {

    private DisposalDtos() {
    }

    // ---------------- ra ----------------

    /** 1 dòng trên màn 폐기조회 */
    public record Summary(String disposalNo, String storeCode, LocalDate businessDate,
                          DisposalStatus status, String statusCode, String statusName,
                          String remark, String registeredBy, Instant registeredAt,
                          int lineCount, long totalQty, long totalCostAmount, long totalSaleAmount) {
    }

    /** Màn chi tiết */
    public record Detail(String disposalNo, String storeCode, LocalDate businessDate,
                         DisposalStatus status, String statusCode, String statusName, String remark,
                         String registeredBy, Instant registeredAt, String updatedBy, Instant updatedAt,
                         String confirmedBy, Instant confirmedAt,
                         String cancelledBy, Instant cancelledAt, String cancelReason,
                         String confirmCancelledBy, Instant confirmCancelledAt, String confirmCancelReason,
                         long version, long totalQty, long totalCostAmount, long totalSaleAmount,
                         /** 영업일자 của phiếu đã 마감 */
                         boolean closed,
                         /** Thao tác được phép với phiếu (theo trạng thái, 마감 và quyền của người đang xem) */
                         Actions actions,
                         /** Lý do chưa xác nhận được (thiếu tồn…), để app hiển thị trước khi bấm */
                         List<String> issues,
                         List<Line> items) {
    }

    public record Actions(boolean edit, boolean cancel, boolean confirm, boolean cancelConfirm) {
    }

    public record Line(int lineNo, String itemCode, String itemName, long qty,
                       DisposalReason reasonCode, String reasonName,
                       long costPrice, long salePrice, long costAmount, long saleAmount,
                       Long onHandQty, Long availableQty,
                       /** Đủ tồn khả dụng để hủy (chỉ có nghĩa khi phiếu còn 등록) */
                       boolean sufficient) {
    }

    /** Tra 1 mặt hàng khi quét barcode trên màn đăng ký */
    public record ItemInfo(String itemCode, String itemName, long onHandQty, long availableQty,
                           long costPrice, long salePrice) {
    }

    public record ReasonInfo(DisposalReason code, String koreanName, String vietnameseName) {
    }

    public record ClosingInfo(String storeCode, LocalDate currentBusinessDate, LocalDate lastClosedDate) {
    }

    // ---------------- vào ----------------

    /** Đăng ký (POST) / sửa (PUT, cần version) */
    public record SaveRequest(Long version, String remark, List<LineInput> items) {
    }

    public record LineInput(String itemCode, Long qty, DisposalReason reasonCode) {
    }

    /** Xác nhận / hủy phiếu / hủy xác nhận */
    public record ActionRequest(Long version, String reason) {
    }

    public record ClosingRequest(LocalDate closeDate) {
    }

    /** Lỗi nghiệp vụ */
    public record Error(String code, String message, List<String> details) {
    }
}
