package com.example.andemo.fcm;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.andemo.alert.AlertDispatcher;
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
        Log.d(TAG, "New FCM token: " + token);

        // TODO: Gửi token này lên backend của bạn
        // sendTokenToServer(token);
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
        } else {
            Log.d(TAG, "Unknown message type: " + type);
        }
    }

    // private void sendTokenToServer(String token) {
    //     // Gọi API backend để lưu token + device info
    // }
}
