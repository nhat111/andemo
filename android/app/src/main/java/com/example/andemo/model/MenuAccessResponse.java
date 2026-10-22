package com.example.andemo.model;

public class MenuAccessResponse {
    private String menuCode;
    private boolean allowed;
    private boolean masked;
    private String message;

    public String getMenuCode() { return menuCode; }
    public boolean isAllowed() { return allowed; }
    public boolean isMasked() { return masked; }
    public String getMessage() { return message; }
}
