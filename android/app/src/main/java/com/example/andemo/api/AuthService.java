package com.example.andemo.api;

import com.example.andemo.model.LoginRequest;
import com.example.andemo.model.LoginResponse;
import com.example.andemo.model.RefreshTokenRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthService {
    @POST("api/auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    /** Đổi refresh token lấy access token mới (refresh token cũ bị thu hồi, nhận token mới). */
    @POST("api/auth/refresh")
    Call<LoginResponse> refresh(@Body RefreshTokenRequest request);

    @POST("api/auth/logout")
    Call<Void> logout(@Body RefreshTokenRequest request);
}
