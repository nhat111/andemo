package com.example.andemo.command;

import android.content.Context;
import android.util.Log;

import com.example.andemo.BuildConfig;
import com.example.andemo.fcm.FcmTokenRegistrar;
import com.example.andemo.polling.AlertPoller;
import com.example.andemo.polling.PdaPollingService;
import com.example.andemo.websocket.PdaWebSocketService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kênh nhận lệnh tìm PDA theo flavor lúc build (app/build.gradle): polling, websocket, fcm.
 *
 * - websocket / polling: foreground service chạy suốt (có notification thường trực).
 * - fcm: không có service chạy nền; Google Play services đánh thức app khi có lệnh.
 *   Cần app/google-services.json; thiếu thì tự chuyển sang polling.
 * Chỉ đăng ký FCM token ở chế độ fcm, để 3 cách so sánh được độc lập với nhau.
 */
public final class CommandChannel {

    private static final String TAG = "CommandChannel";
    // 1 thread cho việc poll bắt kịp ở chế độ fcm (gọi API đồng bộ, không chạy trên main thread)
    private static final ExecutorService CATCH_UP = Executors.newSingleThreadExecutor();

    private CommandChannel() {
    }

    public static void start(Context context) {
        String channel = effectiveChannel(context);
        Log.d(TAG, "Command channel: " + channel);
        switch (channel) {
            case "fcm":
                PdaWebSocketService.stop(context);
                PdaPollingService.stop(context);
                FcmTokenRegistrar.registerCurrentToken(context);
                // FCM có thể làm rơi message (máy tắt quá TTL, bị force-stop…): mở app là hỏi bù 1 lần
                Context appContext = context.getApplicationContext();
                CATCH_UP.execute(() -> AlertPoller.pollOnce(appContext));
                break;
            case "polling":
                PdaPollingService.start(context);
                break;
            default:
                PdaWebSocketService.start(context);
        }
    }

    public static void stop(Context context) {
        PdaWebSocketService.stop(context);
        PdaPollingService.stop(context);
        // Xóa token trên máy: server gửi tới token cũ sẽ bị FCM báo UNREGISTERED và tự gỡ
        FcmTokenRegistrar.deleteToken(context);
    }

    public static boolean isFcm(Context context) {
        return "fcm".equals(effectiveChannel(context));
    }

    private static String effectiveChannel(Context context) {
        String channel = BuildConfig.PDA_COMMAND_CHANNEL;
        if ("fcm".equals(channel) && !FcmTokenRegistrar.isFirebaseConfigured(context)) {
            Log.w(TAG, "FCM flavor but app/google-services.json is missing: falling back to polling");
            return "polling";
        }
        return channel;
    }
}
