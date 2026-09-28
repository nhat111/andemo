package com.example.andemo.command;

import android.content.Context;

import com.example.andemo.BuildConfig;
import com.example.andemo.polling.PdaPollingService;
import com.example.andemo.websocket.PdaWebSocketService;

/**
 * Chọn kênh nhận lệnh tìm PDA lúc build: -PpdaChannel=websocket (mặc định) hoặc polling.
 * FCM (nếu có google-services.json) luôn chạy song song, chống trùng nằm ở AlertDispatcher.
 */
public final class CommandChannel {

    private CommandChannel() {
    }

    public static void start(Context context) {
        if (useWebSocket()) {
            PdaWebSocketService.start(context);
        } else {
            PdaPollingService.start(context);
        }
    }

    public static void stop(Context context) {
        PdaWebSocketService.stop(context);
        PdaPollingService.stop(context);
    }

    private static boolean useWebSocket() {
        return "websocket".equals(BuildConfig.PDA_COMMAND_CHANNEL);
    }
}
