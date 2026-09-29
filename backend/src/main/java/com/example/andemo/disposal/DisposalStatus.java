package com.example.andemo.disposal;

/**
 * Trạng thái phiếu hủy (폐기전표) theo cách các hệ thống bán lẻ Hàn Quốc hay dùng: mã số + tên.
 *
 * <pre>
 *            수정 (sửa)
 *             ┌──┐
 *             ▼  │
 *   ──등록──► 10 REGISTERED ──확정──► 20 CONFIRMED
 *             │      ▲                   │
 *        취소 │      └──── 확정취소 ─────┘  (chỉ khi 영업일자 chưa 마감)
 *             ▼
 *           90 CANCELLED
 * </pre>
 */
public enum DisposalStatus {
    /** 10 등록: đã đăng ký, chưa trừ tồn; sửa / hủy phiếu được */
    REGISTERED("10", "등록"),
    /** 20 확정: đã xác nhận, đã trừ tồn và ghi 수불 */
    CONFIRMED("20", "확정"),
    /** 90 취소: đã hủy phiếu (chưa từng trừ tồn, hoặc đã 확정취소 rồi hủy) */
    CANCELLED("90", "취소");

    private final String code;
    private final String koreanName;

    DisposalStatus(String code, String koreanName) {
        this.code = code;
        this.koreanName = koreanName;
    }

    public String getCode() {
        return code;
    }

    public String getKoreanName() {
        return koreanName;
    }
}
