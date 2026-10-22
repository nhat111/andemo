package com.example.andemo.model;

import java.util.List;

public class LockRequest {
    private List<String> menuCodes;

    public LockRequest(List<String> menuCodes) {
        this.menuCodes = menuCodes;
    }

    public List<String> getMenuCodes() { return menuCodes; }
}
