package com.example.andemo.dto;

import com.example.andemo.entity.PdaAlertStatus;
import lombok.Data;

@Data
public class PdaAlertAckRequest {
    private String deviceId;
    private PdaAlertStatus status;
}
