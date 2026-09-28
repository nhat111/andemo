package com.example.andemo.model;

public class LoginResponse {
    private String token;
    private String role;
    private String username;
    private String refreshToken;

    public String getToken() { return token; }
    public String getRole() { return role; }
    public String getUsername() { return username; }
    public String getRefreshToken() { return refreshToken; }
}
