package com.example.andemo.dto;

import com.example.andemo.entity.PdaAlert;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Lệnh gửi xuống PDA: trả về từ GET /api/pda/alerts/pending và đẩy qua WebSocket
 * (qua WebSocket có thêm type = "PDA_FINDER_ALERT").
 */
@Data
@AllArgsConstructor
public class PdaAlertCommand {
    private String type;
    private String requestId;
    private String message;
    private String storeCode;

    public static PdaAlertCommand from(PdaAlert alert) {
        return new PdaAlertCommand("PDA_FINDER_ALERT", alert.getRequestId(), alert.getMessage(),
                alert.getStoreCode());
    }
}
