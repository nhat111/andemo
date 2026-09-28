package com.example.andemo.push;

import java.time.Duration;
import java.util.Map;

/**
 * Gửi lệnh xuống PDA qua dịch vụ push (FCM). Tách thành interface để test không cần Firebase thật.
 */
public interface PushSender {

    /** false khi chưa cấu hình (không có service account): mọi lệnh gửi đều bị bỏ qua. */
    boolean isEnabled();

    /**
     * Gửi bất đồng bộ 1 data message.
     *
     * @param onInvalidToken gọi khi FCM báo token không còn dùng được (app bị gỡ, token bị xóa…)
     */
    void send(String token, Map<String, String> data, Duration ttl, Runnable onInvalidToken);
}
