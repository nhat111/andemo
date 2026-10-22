package com.example.andemo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MenuStatusDto {
    private String menuCode;
    private String menuName;
    private String lockType;   // BLOCK / MASK
    private boolean locked;
}
