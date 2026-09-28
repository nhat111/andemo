package com.example.andemo.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import com.example.andemo.BuildConfig;

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
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        // 3 app (polling / websocket / fcm) ký cùng khóa nên có cùng ANDROID_ID trên 1 máy.
        // Thêm hậu tố để server coi là 3 PDA riêng, không lẫn kết nối / token / lệnh của nhau.
        return androidId + "-" + BuildConfig.PDA_COMMAND_CHANNEL;
    }

    /** Tên dễ đọc để requester nhận ra máy, ví dụ "realme RMX1851 (polling)". */
    public static String getDeviceName() {
        return Build.MANUFACTURER + " " + Build.MODEL + " (" + BuildConfig.PDA_COMMAND_CHANNEL + ")";
    }
}
