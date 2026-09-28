package com.example.andemo.alert;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

/**
 * Lưu các requestId đã xử lý để không phát alert 2 lần cho cùng một lệnh.
 *
 * Cần lưu bền (SharedPreferences) chứ không chỉ trong RAM vì:
 * - Polling trả lại lệnh cho tới khi server nhận được ack; nếu ack thất bại, lần poll sau
 *   lại thấy đúng lệnh đó.
 * - FCM và polling có thể cùng giao một lệnh.
 * - Process có thể bị kill và khởi động lại giữa hai lần nhận.
 */
final class ProcessedAlertStore {

    private static final String PREF_NAME = "processed_alerts";
    private static final long RETENTION_MS = 24 * 60 * 60 * 1000L;

    private ProcessedAlertStore() {
    }

    /**
     * Đánh dấu requestId là đã xử lý.
     *
     * @return true nếu requestId chưa từng được xử lý (lần đầu thấy lệnh này)
     */
    static synchronized boolean markIfNew(Context context, String requestId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (prefs.contains(requestId)) {
            return false;
        }

        long now = System.currentTimeMillis();
        SharedPreferences.Editor editor = prefs.edit();
        // Dọn các requestId cũ để file không phình ra mãi
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            Object markedAt = entry.getValue();
            if (!(markedAt instanceof Long) || now - (Long) markedAt > RETENTION_MS) {
                editor.remove(entry.getKey());
            }
        }
        editor.putLong(requestId, now).apply();
        return true;
    }
}
