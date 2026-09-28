package com.example.andemo.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Refresh token. Chỉ lưu hash (SHA-256): lộ database cũng không dùng được token.
 */
@Entity
@Table(name = "refresh_token", indexes = @Index(columnList = "username"))
@Data
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    // khác null = đã bị thu hồi (đã dùng để xoay vòng, logout, hoặc phát hiện dùng lại)
    private Instant revokedAt;

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
