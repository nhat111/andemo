package com.example.andemo.disposal;

/** 1 dòng đang soạn trên màn đăng ký / sửa (chưa lưu lên server). */
class EditLine {
    final String itemCode;
    final String itemName;
    long qty;
    String reasonCode;
    String reasonLabel;
    /** Tồn khả dụng lúc quét (null khi mở phiếu cũ để sửa: server sẽ kiểm tra lúc xác nhận) */
    Long availableQty;
    final long costPrice;

    EditLine(String itemCode, String itemName, long qty, String reasonCode, String reasonLabel,
             Long availableQty, long costPrice) {
        this.itemCode = itemCode;
        this.itemName = itemName;
        this.qty = qty;
        this.reasonCode = reasonCode;
        this.reasonLabel = reasonLabel;
        this.availableQty = availableQty;
        this.costPrice = costPrice;
    }
}
