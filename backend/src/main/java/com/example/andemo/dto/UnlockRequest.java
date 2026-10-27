package com.example.andemo.dto;

import lombok.Data;

@Data
public class UnlockRequest {
    private String menuCode; // null → mở khóa tất cả (nút Mở khóa trên title)
    private String pin;
}
