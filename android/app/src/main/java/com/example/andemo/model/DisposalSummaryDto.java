package com.example.andemo.model;

/** 1 dòng trên màn danh sách phiếu hủy (GET /api/disposals). */
public class DisposalSummaryDto {
    private String disposalNo;
    private String warehouseCode;
    private String status;
    private String reason;
    private String requestedBy;
    private String requestedAt;
    private int lineCount;
    private long totalQty;

    public String getDisposalNo() { return disposalNo; }
    public String getWarehouseCode() { return warehouseCode; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public String getRequestedBy() { return requestedBy; }
    public String getRequestedAt() { return requestedAt; }
    public int getLineCount() { return lineCount; }
    public long getTotalQty() { return totalQty; }
}
