package com.example.andemo.model;

/** 1 dòng trên màn 폐기조회 (GET /api/disposals). */
public class DisposalSummaryDto {
    private String disposalNo;
    private String storeCode;
    private String businessDate;
    private String status;
    private String statusCode;
    private String statusName;
    private String remark;
    private String registeredBy;
    private String registeredAt;
    private int lineCount;
    private long totalQty;
    private long totalCostAmount;
    private long totalSaleAmount;

    public String getDisposalNo() { return disposalNo; }
    public String getStoreCode() { return storeCode; }
    public String getBusinessDate() { return businessDate; }
    public String getStatus() { return status; }
    public String getStatusCode() { return statusCode; }
    public String getStatusName() { return statusName; }
    public String getRemark() { return remark; }
    public String getRegisteredBy() { return registeredBy; }
    public String getRegisteredAt() { return registeredAt; }
    public int getLineCount() { return lineCount; }
    public long getTotalQty() { return totalQty; }
    public long getTotalCostAmount() { return totalCostAmount; }
    public long getTotalSaleAmount() { return totalSaleAmount; }
}
