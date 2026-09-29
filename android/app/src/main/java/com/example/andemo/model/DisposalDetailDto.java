package com.example.andemo.model;

import java.util.List;

/** Chi tiết phiếu hủy (GET /api/disposals/{no}, và kết quả của POST /confirm). */
public class DisposalDetailDto {
    private String disposalNo;
    private String warehouseCode;
    private String status;
    private String reason;
    private String requestedBy;
    private String requestedAt;
    private String confirmedBy;
    private String confirmedAt;
    private long version;
    private boolean confirmable;
    private List<String> issues;
    private List<Line> items;

    public String getDisposalNo() { return disposalNo; }
    public String getWarehouseCode() { return warehouseCode; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public String getRequestedBy() { return requestedBy; }
    public String getRequestedAt() { return requestedAt; }
    public String getConfirmedBy() { return confirmedBy; }
    public String getConfirmedAt() { return confirmedAt; }
    public long getVersion() { return version; }
    public boolean isConfirmable() { return confirmable; }
    public List<String> getIssues() { return issues; }
    public List<Line> getItems() { return items; }

    public static class Line {
        private int lineNo;
        private String itemCode;
        private String itemName;
        private long qty;
        private String reasonCode;
        private Long onHandQty;
        private Long availableQty;
        private boolean sufficient;

        public int getLineNo() { return lineNo; }
        public String getItemCode() { return itemCode; }
        public String getItemName() { return itemName; }
        public long getQty() { return qty; }
        public String getReasonCode() { return reasonCode; }
        public Long getOnHandQty() { return onHandQty; }
        public Long getAvailableQty() { return availableQty; }
        public boolean isSufficient() { return sufficient; }
    }
}
