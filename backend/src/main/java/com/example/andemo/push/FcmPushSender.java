package com.example.andemo.push;

import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Gửi FCM bằng Firebase Admin SDK.
 *
 * Cấu hình (1 trong 2, đặt bằng biến môi trường):
 * - FIREBASE_SERVICE_ACCOUNT_JSON: nội dung file service account JSON (dùng trên Render)
 * - FIREBASE_SERVICE_ACCOUNT_FILE: đường dẫn tới file đó (dùng khi chạy local)
 * Không đặt gì thì FCM tắt, backend vẫn chạy bình thường với polling / WebSocket.
 */
@Slf4j
@Component
public class FcmPushSender implements PushSender {

    private static final String APP_NAME = "andemo-fcm";

    private final FirebaseMessaging messaging;

    public FcmPushSender(@Value("${firebase.service-account-json:}") String serviceAccountJson,
                         @Value("${firebase.service-account-file:}") String serviceAccountFile) {
        this.messaging = init(serviceAccountJson, serviceAccountFile);
    }

    private static FirebaseMessaging init(String json, String file) {
        if (isBlank(json) && isBlank(file)) {
            log.info("FCM disabled: FIREBASE_SERVICE_ACCOUNT_JSON / FIREBASE_SERVICE_ACCOUNT_FILE not set");
            return null;
        }
        try (InputStream credentials = !isBlank(json)
                ? new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
                : new FileInputStream(file)) {
            FirebaseApp app = FirebaseApp.getApps().stream()
                    .filter(existing -> existing.getName().equals(APP_NAME))
                    .findFirst()
                    .orElseGet(() -> initializeApp(credentials));
            log.info("FCM enabled (project {})", app.getOptions().getProjectId());
            return FirebaseMessaging.getInstance(app);
        } catch (IOException | RuntimeException e) {
            // Service account sai định dạng / thiếu file: tắt FCM thay vì làm backend không khởi động được
            log.error("FCM disabled: cannot load Firebase service account: {}", e.getMessage());
            return null;
        }
    }

    private static FirebaseApp initializeApp(InputStream credentials) {
        try {
            GoogleCredentials googleCredentials = GoogleCredentials.fromStream(credentials);
            FirebaseOptions.Builder options = FirebaseOptions.builder().setCredentials(googleCredentials);
            // Ghi rõ project id (lấy từ service account) để log in đúng project đang dùng
            if (googleCredentials instanceof ServiceAccountCredentials serviceAccount) {
                options.setProjectId(serviceAccount.getProjectId());
            }
            return FirebaseApp.initializeApp(options.build(), APP_NAME);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid Firebase service account", e);
        }
    }

    @Override
    public boolean isEnabled() {
        return messaging != null;
    }

    @Override
    public void send(String token, Map<String, String> data, Duration ttl, Runnable onInvalidToken) {
        if (messaging == null) {
            return;
        }
        Message message = Message.builder()
                .setToken(token)
                .putAllData(data)
                .setAndroidConfig(AndroidConfig.builder()
                        // high priority: được giao ngay cả khi máy đang Doze, và cho phép app
                        // khởi động foreground service từ background (Android 12+)
                        .setPriority(AndroidConfig.Priority.HIGH)
                        // PDA offline lâu hơn thời hạn lệnh thì FCM bỏ message, không kêu cho lệnh cũ
                        .setTtl(Math.max(0, ttl.toMillis()))
                        .build())
                .build();

        ApiFutures.addCallback(messaging.sendAsync(message), new ApiFutureCallback<>() {
            @Override
            public void onSuccess(String messageId) {
                log.info("FCM sent {} for requestId={}", messageId, data.get("requestId"));
            }

            @Override
            public void onFailure(Throwable t) {
                if (t instanceof FirebaseMessagingException e
                        && (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                        || e.getMessagingErrorCode() == MessagingErrorCode.SENDER_ID_MISMATCH)) {
                    log.info("FCM token no longer valid, removing it: {}", e.getMessagingErrorCode());
                    onInvalidToken.run();
                } else {
                    log.warn("FCM send failed for requestId={}: {}", data.get("requestId"), t.getMessage());
                }
            }
        }, MoreExecutors.directExecutor());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
