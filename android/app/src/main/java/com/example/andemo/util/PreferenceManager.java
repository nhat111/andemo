package com.example.andemo.util;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {
    private static final String PREF_NAME = "AndemoPref";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_ROLE = "role";
    private static final String KEY_USERNAME = "username";

    private final SharedPreferences prefs;

    public PreferenceManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveLogin(String token, String refreshToken, String role, String username) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_ROLE, role)
                .putString(KEY_USERNAME, username)
                .apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
    }

    /**
     * Lưu cặp token mới sau khi làm mới. commit() (ghi đồng bộ) thay vì apply(): refresh token cũ
     * đã bị server thu hồi, nếu process chết trước khi ghi xong thì lần sau dùng token cũ sẽ bị
     * coi là dùng lại và bị buộc login lại.
     */
    public void updateTokens(String token, String refreshToken) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .commit();
    }

    public String getRole() {
        return prefs.getString(KEY_ROLE, null);
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, null);
    }

    public boolean isLoggedIn() {
        return getToken() != null;
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
