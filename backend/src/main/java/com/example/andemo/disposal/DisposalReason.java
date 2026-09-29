package com.example.andemo.disposal;

/** 폐기사유 (lý do hủy) thường gặp ở cửa hàng tiện lợi / siêu thị Hàn Quốc. */
public enum DisposalReason {
    EXPIRED("유통기한 경과", "Hết hạn sử dụng"),
    DAMAGED("파손", "Hư hỏng, vỡ"),
    SPOILED("변질", "Biến chất, hư thối"),
    RECALL("리콜", "Thu hồi theo NCC / cơ quan"),
    QUALITY("품질불량", "Lỗi chất lượng"),
    OTHER("기타", "Khác");

    private final String koreanName;
    private final String vietnameseName;

    DisposalReason(String koreanName, String vietnameseName) {
        this.koreanName = koreanName;
        this.vietnameseName = vietnameseName;
    }

    public String getKoreanName() {
        return koreanName;
    }

    public String getVietnameseName() {
        return vietnameseName;
    }
}
