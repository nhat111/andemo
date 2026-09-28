package com.example.andemo.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

/**
 * Định danh thiết bị gửi lên server.
 *
 * Bản POC dùng ANDROID_ID: đổi khi factory reset, và từ Android 8 khác nhau theo khóa ký app.
 * Production nên dùng mã tài sản từ MDM (xem docs/PDA_FINDER_MECHANISM_OPTIONS.md, mục 4.1).
 */
public final class DeviceIdProvider {

    private DeviceIdProvider() {
    }

    @SuppressLint("HardwareIds")
    public static String get(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    /** Tên dễ đọc để requester nhận ra máy, ví dụ "realme RMX1851". */
    public static String getDeviceName() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }
}
