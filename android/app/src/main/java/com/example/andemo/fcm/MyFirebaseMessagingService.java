package com.example.andemo.fcm;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.andemo.alert.AlertAckReporter;
import com.example.andemo.alert.AlertDispatcher;
import com.example.andemo.command.CommandChannel;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Nhận FCM message và kích hoạt PDA Finder Alert.
 *
 * Lưu ý quan trọng:
 * - Phải gửi Data Message từ server (không phải Notification Message)
 *   thì onMessageReceived mới được gọi khi app bị kill / background.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token received");

        // Firebase đổi token (cài lại app, xóa dữ liệu, token hết hạn…): báo lại server.
        // Chỉ ở chế độ fcm, để polling / WebSocket so sánh được độc lập.
        if (CommandChannel.isFcm(this)) {
            FcmTokenRegistrar.send(this, token);
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "From: " + remoteMessage.getFrom());

        Map<String, String> data = remoteMessage.getData();
        if (data.isEmpty()) {
            Log.w(TAG, "Received message with empty data payload");
            return;
        }

        String type = data.get("type");
        if ("PDA_FINDER_ALERT".equals(type)) {
            String requestId = data.get("requestId");
            String message = data.get("message");
            String storeCode = data.get("storeCode");

            if (message == null || message.isEmpty()) {
                message = "PDA đang được tìm kiếm";
            }

            // Chống trùng (FCM gửi lại, hoặc polling đã giao cùng lệnh) nằm trong AlertDispatcher
            AlertDispatcher.dispatch(this, requestId, message, storeCode);
            // Báo server đã nhận (giống polling / WebSocket): web quản lý chuyển sang "đang đổ chuông"
            AlertAckReporter.report(this, requestId, AlertAckReporter.STATUS_DELIVERED);
        } else {
            Log.d(TAG, "Unknown message type: " + type);
        }
    }
}
