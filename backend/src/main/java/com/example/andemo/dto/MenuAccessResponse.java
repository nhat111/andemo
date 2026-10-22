package com.example.andemo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MenuAccessResponse {
    private String menuCode;
    private boolean allowed;   // false = menu BLOCK đang bị khóa
    private boolean masked;    // true = menu MASK đang bị khóa → ẩn cột nhạy cảm
    private String message;
}
