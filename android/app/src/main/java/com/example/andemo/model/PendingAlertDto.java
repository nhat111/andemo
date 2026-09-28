package com.example.andemo.model;

/**
 * Lệnh tìm PDA, nhận từ GET /api/pda/alerts/pending hoặc qua WebSocket
 * (qua WebSocket có thêm type = "PDA_FINDER_ALERT").
 */
public class PendingAlertDto {
    private String type;
    private String requestId;
    private String message;
    private String storeCode;

    public String getType() { return type; }
    public String getRequestId() { return requestId; }
    public String getMessage() { return message; }
    public String getStoreCode() { return storeCode; }
}
