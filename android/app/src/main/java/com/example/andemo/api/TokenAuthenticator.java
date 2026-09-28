package com.example.andemo.api;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * OkHttp gọi class này khi server trả 401: làm mới access token rồi gửi lại đúng request đó.
 * Code gọi API (poll, ack, màn hình…) không cần biết token đã hết hạn.
 */
public class TokenAuthenticator implements Authenticator {

    private static final String BEARER = "Bearer ";

    private final Context context;

    public TokenAuthenticator(Context context) {
        this.context = context.getApplicationContext();
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) {
        if (response.priorResponse() != null) {
            return null; // đã thử lại 1 lần mà vẫn 401: dừng, tránh lặp vô hạn
        }
        String header = response.request().header("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            return null; // request không có token (ví dụ login sai mật khẩu): không phải chuyện hết hạn
        }

        String newToken = TokenRefresher.refresh(context, header.substring(BEARER.length()));
        if (newToken == null) {
            return null; // trả nguyên 401 cho code gọi API
        }
        return response.request().newBuilder()
                .header("Authorization", BEARER + newToken)
                .build();
    }
}
