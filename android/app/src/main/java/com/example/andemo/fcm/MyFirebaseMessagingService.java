package com.example.andemo.fcm;

import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.andemo.alert.PdaAlertService;
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

            if (requestId == null || requestId.isEmpty()) {
                Log.w(TAG, "Missing requestId, ignore alert");
                return;
            }

            if (message == null || message.isEmpty()) {
                message = "PDA đang được tìm kiếm";
            }

            startAlertService(requestId, message, storeCode);
        } else {
            Log.d(TAG, "Unknown message type: " + type);
        }
    }

    private void startAlertService(String requestId, String message, String storeCode) {
        Intent intent = new Intent(this, PdaAlertService.class);
        intent.putExtra(PdaAlertService.EXTRA_REQUEST_ID, requestId);
        intent.putExtra(PdaAlertService.EXTRA_MESSAGE, message);
        if (storeCode != null) {
            intent.putExtra(PdaAlertService.EXTRA_STORE_CODE, storeCode);
        }

        ContextCompat.startForegroundService(this, intent);
        Log.d(TAG, "Started PdaAlertService for requestId=" + requestId);
    }

    // private void sendTokenToServer(String token) {
    //     // Gọi API backend để lưu token + device info
    // }
}
