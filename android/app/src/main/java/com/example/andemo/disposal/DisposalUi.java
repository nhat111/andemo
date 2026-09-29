package com.example.andemo.disposal;

import android.graphics.Color;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Hiển thị dùng chung cho các màn 폐기: tên trạng thái, màu, tiền, ngày giờ. */
final class DisposalUi {

    private DisposalUi() {
    }

    static String statusLabel(String status) {
        if ("REGISTERED".equals(status)) return "Đã đăng ký (등록)";
        if ("CONFIRMED".equals(status)) return "Đã xác nhận (확정)";
        if ("CANCELLED".equals(status)) return "Đã hủy (취소)";
        return status;
    }

    static int statusColor(String status) {
        if ("REGISTERED".equals(status)) return Color.rgb(0xE6, 0x51, 0x00);  // cam
        if ("CONFIRMED".equals(status)) return Color.rgb(0x2E, 0x7D, 0x32);  // xanh lá
        return Color.GRAY;
    }

    /** 12,345원 */
    static String won(long amount) {
        return NumberFormat.getIntegerInstance(Locale.US).format(amount) + "원";
    }

    /**
     * Server trả thời gian ISO UTC, ví dụ 2026-09-29T02:43:47.950Z. minSdk 24 chưa có java.time
     * nên dùng SimpleDateFormat: bỏ phần lẻ giây, đọc theo UTC, hiển thị theo giờ máy.
     */
    static String formatTime(String iso) {
        if (iso == null || iso.length() < 19) {
            return iso == null ? "" : iso;
        }
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = in.parse(iso.substring(0, 19));
            return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(date);
        } catch (ParseException e) {
            return iso;
        }
    }
}
