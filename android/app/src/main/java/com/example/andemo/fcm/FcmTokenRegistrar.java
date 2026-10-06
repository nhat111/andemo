package com.example.andemo.fcm;

import android.content.Context;
import android.util.Log;

import com.example.andemo.BuildConfig;
import com.example.andemo.api.AlertApi;
import com.example.andemo.api.ApiClient;
import com.example.andemo.log.DeviceLog;
import com.example.andemo.model.FcmTokenRequest;
import com.example.andemo.util.DeviceIdProvider;
import com.example.andemo.util.PreferenceManager;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Vòng đời FCM token (bug T1 trong tracker): lấy token sau khi login, gửi lên server,
 * gửi lại khi Firebase cấp token mới (onNewToken), xóa khi logout.
 *
 * Gửi 1 lần, không retry: lần mở app sau (CommandChannel.start) sẽ gửi lại.
 */
public final class FcmTokenRegistrar {

    private static final String TAG = "FcmTokenRegistrar";

    private FcmTokenRegistrar() {
    }

    /** Có google-services.json lúc build thì Firebase tự khởi tạo khi app chạy. */
    public static boolean isFirebaseConfigured(Context context) {
        return !FirebaseApp.getApps(context).isEmpty();
    }

    public static void registerCurrentToken(Context context) {
        if (!isFirebaseConfigured(context)) {
            return;
        }
        Context appContext = context.getApplicationContext();
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                send(appContext, task.getResult());
            } else {
                // Thường gặp: máy không có Google Play services, hoặc không vào được máy chủ Google
                DeviceLog.w(TAG, "Cannot get FCM token", task.getException());
            }
        });
    }

    public static void send(Context context, String token) {
        if (!new PreferenceManager(context).isLoggedIn()) {
            return; // chưa login: sẽ gửi sau khi login
        }
        AlertApi api = ApiClient.getClient(context).create(AlertApi.class);
        FcmTokenRequest body = new FcmTokenRequest(token, DeviceIdProvider.getDeviceName());
        api.updateFcmToken(DeviceIdProvider.get(context), body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    DeviceLog.d(TAG, "FCM token registered with server");
                    if (BuildConfig.DEBUG) {
                        // Chỉ bản debug: để gửi thử từ Firebase Console (Messaging → Send test message)
                        Log.d(TAG, "FCM token: " + token); // không ghi file: token là dữ liệu nhạy cảm
                    }
                } else {
                    DeviceLog.w(TAG, "FCM token rejected: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                DeviceLog.w(TAG, "FCM token registration failed: " + t.getMessage());
            }
        });
    }

    public static void deleteToken(Context context) {
        if (isFirebaseConfigured(context)) {
            FirebaseMessaging.getInstance().deleteToken();
        }
    }
}
