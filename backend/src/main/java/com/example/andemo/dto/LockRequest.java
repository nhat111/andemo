package com.example.andemo.dto;

import lombok.Data;

import java.util.List;

@Data
public class LockRequest {
    private List<String> menuCodes;
}
