package com.example.andemo.api;

import android.content.Context;

import com.example.andemo.util.PreferenceManager;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Tự động gắn header: Authorization: Bearer <token>
 */
public class AuthInterceptor implements Interceptor {

    private final PreferenceManager pref;

    public AuthInterceptor(Context context) {
        this.pref = new PreferenceManager(context);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();

        String token = pref.getToken();
        if (token == null || token.isEmpty()) {
            // Chưa login → gửi request gốc
            return chain.proceed(original);
        }

        // Có token → gắn Bearer
        Request newRequest = original.newBuilder()
                .header("Authorization", "Bearer " + token)
                .build();

        return chain.proceed(newRequest);
    }
}
