package com.example.andemo.model;

import java.util.ArrayList;
import java.util.List;

/** Body đăng ký (POST /api/disposals) và sửa (PUT /api/disposals/{no}, cần version). */
public class DisposalSaveRequest {
    private final Long version;
    private final String remark;
    private final List<Line> items = new ArrayList<>();

    public DisposalSaveRequest(Long version, String remark) {
        this.version = version;
        this.remark = remark;
    }

    public void addLine(String itemCode, long qty, String reasonCode) {
        items.add(new Line(itemCode, qty, reasonCode));
    }

    public static class Line {
        private final String itemCode;
        private final long qty;
        private final String reasonCode;

        Line(String itemCode, long qty, String reasonCode) {
            this.itemCode = itemCode;
            this.qty = qty;
            this.reasonCode = reasonCode;
        }
    }
}
