package com.example.andemo.rules;

import java.util.Locale;

/**
 * Quy tắc phía app của chức năng hủy hàng, viết bằng Java THUẦN:
 * không import android.*, org.json, Retrofit… và không dùng java.time (minSdk 24).
 *
 * Nhờ vậy test được bằng 1 hàm main, không cần Gradle / emulator:
 * android/devcheck/DisposalRulesCheck.java (cách chạy: docs/nexacro-migration/WORK_WITHOUT_BUILD.md mục 4–6).
 */
public final class DisposalRules {

    /** R10: phiếu có tổng giá vốn từ mức này phải xác nhận thêm 1 lần */
    public static final long BIG_AMOUNT = 100_000;
    /** R5: ghi chú tối đa 100 ký tự */
    public static final int REMARK_MAX = 100;

    private DisposalRules() {
    }

    /** R10 */
    public static boolean needsSecondConfirm(long totalCostAmount) {
        return totalCostAmount >= BIG_AMOUNT;
    }

    /** R5: null = hợp lệ, ngược lại là thông báo lỗi */
    public static String remarkError(String remark) {
        if (remark != null && remark.length() > REMARK_MAX) {
            return "Tối đa " + REMARK_MAX + " ký tự";
        }
        return null;
    }

    /** R3: thiếu tồn khi không biết tồn khả dụng (không có trong tồn kho) hoặc hủy nhiều hơn khả dụng */
    public static boolean isShortage(long qty, Long availableQty) {
        return availableQty == null || qty > availableQty;
    }

    /** R1: từ ngày ≤ đến ngày, định dạng yyyy-MM-dd (so chuỗi được vì cùng định dạng); trống = không giới hạn */
    public static boolean validPeriod(String from, String to) {
        if (from == null || from.isEmpty() || to == null || to.isEmpty()) {
            return true;
        }
        return from.compareTo(to) <= 0;
    }

    /** 1234567 → "1,234,567원" */
    public static String won(long amount) {
        return String.format(Locale.US, "%,d원", amount);
    }

    /** "1234567" → "1,234,567원"; trống / không phải số → giữ nguyên */
    public static String won(String number) {
        try {
            return won(Long.parseLong(number));
        } catch (NumberFormatException e) {
            return number;
        }
    }

    public static String statusLabel(String status) {
        if ("REGISTERED".equals(status)) return "Đã đăng ký (등록)";
        if ("CONFIRMED".equals(status)) return "Đã xác nhận (확정)";
        if ("CANCELLED".equals(status)) return "Đã hủy (취소)";
        return status;
    }
}
