package com.example.andemo.model;

public class MenuStatusDto {
    private String menuCode;
    private String menuName;
    private String lockType; // BLOCK / MASK
    private boolean locked;

    public String getMenuCode() { return menuCode; }
    public String getMenuName() { return menuName; }
    public String getLockType() { return lockType; }
    public boolean isLocked() { return locked; }
}
