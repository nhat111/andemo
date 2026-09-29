package com.example.andemo.model;

/** Body của xác nhận / hủy phiếu / hủy xác nhận: version đang hiển thị + lý do (khi hủy). */
public class DisposalActionRequest {
    private final long version;
    private final String reason;

    public DisposalActionRequest(long version, String reason) {
        this.version = version;
        this.reason = reason;
    }
}
