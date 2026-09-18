package com.example.andemo.api;

import com.example.andemo.model.LoginRequest;
import com.example.andemo.model.LoginResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthService {
    @POST("api/auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);
}
