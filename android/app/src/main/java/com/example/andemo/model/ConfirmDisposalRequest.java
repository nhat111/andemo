package com.example.andemo.model;

/** Body của POST /api/disposals/{no}/confirm: version đang hiển thị, để server phát hiện dữ liệu cũ. */
public class ConfirmDisposalRequest {
    private final long version;

    public ConfirmDisposalRequest(long version) {
        this.version = version;
    }
}
