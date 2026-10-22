package com.example.andemo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MenuLockLogDto {
    private Long logId;
    private String storeId;
    private String menuCode;
    private String actionType;
    private String actorId;
    private String createdAt;
    private String ipAddress;
}
