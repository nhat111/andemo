package com.example.andemo.model;

import java.util.List;

/** Chi tiết phiếu hủy (GET /api/disposals/{no}; cũng là kết quả của đăng ký / sửa / xác nhận…). */
public class DisposalDetailDto {
    private String disposalNo;
    private String storeCode;
    private String businessDate;
    private String status;
    private String statusCode;
    private String statusName;
    private String remark;
    private String registeredBy;
    private String registeredAt;
    private String updatedBy;
    private String updatedAt;
    private String confirmedBy;
    private String confirmedAt;
    private String cancelledBy;
    private String cancelledAt;
    private String cancelReason;
    private String confirmCancelledBy;
    private String confirmCancelledAt;
    private String confirmCancelReason;
    private long version;
    private long totalQty;
    private long totalCostAmount;
    private long totalSaleAmount;
    private boolean closed;
    private Actions actions;
    private List<String> issues;
    private List<Line> items;

    public String getDisposalNo() { return disposalNo; }
    public String getStoreCode() { return storeCode; }
    public String getBusinessDate() { return businessDate; }
    public String getStatus() { return status; }
    public String getStatusCode() { return statusCode; }
    public String getStatusName() { return statusName; }
    public String getRemark() { return remark; }
    public String getRegisteredBy() { return registeredBy; }
    public String getRegisteredAt() { return registeredAt; }
    public String getUpdatedBy() { return updatedBy; }
    public String getUpdatedAt() { return updatedAt; }
    public String getConfirmedBy() { return confirmedBy; }
    public String getConfirmedAt() { return confirmedAt; }
    public String getCancelledBy() { return cancelledBy; }
    public String getCancelledAt() { return cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public String getConfirmCancelledBy() { return confirmCancelledBy; }
    public String getConfirmCancelledAt() { return confirmCancelledAt; }
    public String getConfirmCancelReason() { return confirmCancelReason; }
    public long getVersion() { return version; }
    public long getTotalQty() { return totalQty; }
    public long getTotalCostAmount() { return totalCostAmount; }
    public long getTotalSaleAmount() { return totalSaleAmount; }
    public boolean isClosed() { return closed; }
    public Actions getActions() { return actions == null ? new Actions() : actions; }
    public List<String> getIssues() { return issues; }
    public List<Line> getItems() { return items; }

    /** Thao tác server cho phép với người đang xem (theo trạng thái, 마감, quyền) */
    public static class Actions {
        private boolean edit;
        private boolean cancel;
        private boolean confirm;
        private boolean cancelConfirm;

        public boolean isEdit() { return edit; }
        public boolean isCancel() { return cancel; }
        public boolean isConfirm() { return confirm; }
        public boolean isCancelConfirm() { return cancelConfirm; }
    }

    public static class Line {
        private int lineNo;
        private String itemCode;
        private String itemName;
        private long qty;
        private String reasonCode;
        private String reasonName;
        private long costPrice;
        private long salePrice;
        private long costAmount;
        private long saleAmount;
        private Long onHandQty;
        private Long availableQty;
        private boolean sufficient;

        public int getLineNo() { return lineNo; }
        public String getItemCode() { return itemCode; }
        public String getItemName() { return itemName; }
        public long getQty() { return qty; }
        public String getReasonCode() { return reasonCode; }
        public String getReasonName() { return reasonName; }
        public long getCostPrice() { return costPrice; }
        public long getSalePrice() { return salePrice; }
        public long getCostAmount() { return costAmount; }
        public long getSaleAmount() { return saleAmount; }
        public Long getOnHandQty() { return onHandQty; }
        public Long getAvailableQty() { return availableQty; }
        public boolean isSufficient() { return sufficient; }
    }
}
