package com.example.andemo.command;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

/**
 * Notification thường trực dùng chung cho service chờ lệnh (polling hoặc WebSocket).
 * Foreground service bắt buộc có notification, xem docs/PDA_POLLING_DESIGN.md mục 5.1.
 */
public final class CommandNotification {

    public static final int NOTIFICATION_ID = 2001;

    // Importance của channel không đổi được sau khi tạo, nên đổi importance = đổi sang ID mới
    private static final String CHANNEL_ID = "pda_polling_min_channel";
    private static final String OLD_CHANNEL_ID = "pda_polling_channel";

    private CommandNotification() {
    }

    public static Notification build(Context context) {
        createChannel(context);
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(com.example.andemo.R.string.app_name))
                .setContentText("Đang chờ lệnh tìm PDA")
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setPriority(NotificationCompat.PRIORITY_MIN) // cho Android 7.x (chưa có channel)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) {
                return;
            }
            // Xóa channel IMPORTANCE_LOW của bản POC trước (nếu máy đã cài)
            manager.deleteNotificationChannel(OLD_CHANNEL_ID);

            // IMPORTANCE_MIN: không kêu, không có icon trên thanh trạng thái, chỉ nằm thu gọn
            // ở cuối danh sách khi kéo thanh thông báo xuống. Không bỏ được nút Stop trong
            // "Active apps" (Android 13+).
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Chờ lệnh tìm PDA",
                    NotificationManager.IMPORTANCE_MIN
            );
            channel.setDescription("Hiển thị khi app đang chờ lệnh tìm PDA từ server");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
    }
}
