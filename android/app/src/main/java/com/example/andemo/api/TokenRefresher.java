package com.example.andemo.api;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.andemo.log.DeviceLog;
import com.example.andemo.model.LoginResponse;
import com.example.andemo.model.RefreshTokenRequest;
import com.example.andemo.util.PreferenceManager;

import java.io.IOException;

import retrofit2.Response;

/**
 * Đổi refresh token lấy access token mới. Dùng chung cho REST (TokenAuthenticator) và WebSocket.
 *
 * Gọi đồng bộ, KHÔNG gọi trên main thread.
 */
public final class TokenRefresher {

    private static final String TAG = "TokenRefresher";
    private static final Object LOCK = new Object();

    private TokenRefresher() {
    }

    /**
     * @param failedAccessToken access token vừa bị server từ chối
     * @return access token mới để thử lại; null nếu không làm mới được
     *         (khi đó kiểm tra {@link PreferenceManager#isLoggedIn()}: false = phiên đã hết, phải login lại)
     */
    @Nullable
    public static String refresh(Context context, String failedAccessToken) {
        // Nhiều request cùng bị 401 một lúc (poll, ack, WebSocket): chỉ 1 request làm mới,
        // các request còn lại chờ rồi dùng luôn token mới. Server xoay vòng refresh token,
        // nên 2 lần làm mới song song với cùng 1 token sẽ bị coi là dùng lại và thu hồi hết.
        synchronized (LOCK) {
            PreferenceManager pref = new PreferenceManager(context);
            String currentToken = pref.getToken();
            if (currentToken == null) {
                return null; // đã logout
            }
            if (!currentToken.equals(failedAccessToken)) {
                return currentToken; // thread khác vừa làm mới xong
            }

            String refreshToken = pref.getRefreshToken();
            if (refreshToken == null) {
                // Bản app cũ (login trước khi có refresh token) hoặc mock server: buộc login lại
                DeviceLog.w(TAG, "No refresh token, logging out");
                pref.clear();
                return null;
            }

            AuthService authService = ApiClient.getPublicClient().create(AuthService.class);
            try {
                Response<LoginResponse> response =
                        authService.refresh(new RefreshTokenRequest(refreshToken)).execute();
                LoginResponse body = response.body();
                if (response.isSuccessful() && body != null && body.getToken() != null) {
                    pref.updateTokens(body.getToken(), body.getRefreshToken());
                    DeviceLog.d(TAG, "Access token refreshed");
                    return body.getToken();
                }
                if (response.code() == 401) {
                    // Refresh token hết hạn / bị thu hồi: phiên đã hết, phải login lại
                    DeviceLog.w(TAG, "Refresh token rejected, logging out");
                    pref.clear();
                    return null;
                }
                // Lỗi server (5xx…): giữ phiên, lần sau thử lại
                DeviceLog.w(TAG, "Refresh failed: HTTP " + response.code());
                return null;
            } catch (IOException e) {
                // Mất mạng: giữ phiên, lần sau thử lại
                DeviceLog.w(TAG, "Refresh failed: " + e.getMessage());
                return null;
            }
        }
    }
}
