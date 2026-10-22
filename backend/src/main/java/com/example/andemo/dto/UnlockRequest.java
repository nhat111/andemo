package com.example.andemo.dto;

import lombok.Data;

@Data
public class UnlockRequest {
    private String menuCode;
    private String pin;
}
