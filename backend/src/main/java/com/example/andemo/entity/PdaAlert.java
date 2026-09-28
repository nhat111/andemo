package com.example.andemo.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Lệnh tìm PDA. Contract: docs/PDA_POLLING_DESIGN.md và docs/PDA_WEBSOCKET_DESIGN.md
 */
@Entity
@Table(name = "pda_alert")
@Data
@NoArgsConstructor
public class PdaAlert {

    @Id
    private String requestId;

    // null = gửi cho mọi PDA
    private String deviceId;

    private String storeCode;

    private String message;

    private String requestedBy;

    private Instant createdAt;

    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    private PdaAlertStatus status;

    private Instant deliveredAt;

    private Instant finishedAt;

    /** Hết hạn theo giờ server. Lệnh SENT mà expired = PDA không nhận được kịp. */
    @JsonProperty("expired")
    public boolean isExpired() {
        return expiresAt != null && !expiresAt.isAfter(Instant.now());
    }

    public boolean isFor(String deviceId) {
        return this.deviceId == null || this.deviceId.equals(deviceId);
    }
}
