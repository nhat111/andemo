package com.example.andemo.entity;

/**
 * Trạng thái lệnh tìm PDA. Chỉ đi tới, không đi lùi (xem {@link #canTransitionTo}).
 */
public enum PdaAlertStatus {
    SENT,
    DELIVERED,
    STOPPED_BY_USER,
    TIMED_OUT;

    public boolean isFinal() {
        return this == STOPPED_BY_USER || this == TIMED_OUT;
    }

    /**
     * PDA có thể gửi lại ack cũ (ví dụ DELIVERED sau STOPPED_BY_USER khi lần ack trước thất bại),
     * nên chỉ chấp nhận chuyển tới trạng thái sau hơn.
     */
    public boolean canTransitionTo(PdaAlertStatus next) {
        if (isFinal()) {
            return false;
        }
        if (this == SENT) {
            return next != SENT;
        }
        // DELIVERED
        return next.isFinal();
    }
}
