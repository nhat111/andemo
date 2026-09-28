package com.example.andemo.service;

import com.example.andemo.entity.RefreshToken;
import com.example.andemo.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh token xoay vòng (rotation):
 * - Mỗi lần làm mới, token cũ bị thu hồi và cấp token mới. PDA còn liên lạc với server thì
 *   phiên đăng nhập được gia hạn mãi; không liên lạc quá thời hạn refresh thì phải login lại.
 * - Token đã thu hồi mà bị dùng lại = có thể bị lộ (kẻ gian và máy thật cùng giữ 1 token):
 *   thu hồi toàn bộ refresh token của user đó, buộc login lại.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;

    @Value("${jwt.refresh-expiration-days:30}")
    private long refreshExpirationDays;

    /** Kết quả làm mới: user sở hữu và refresh token mới (dạng gốc, chỉ trả cho client 1 lần). */
    public record Rotation(String username, String refreshToken) {
    }

    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException(String message) {
            super(message);
        }
    }

    @Transactional
    public String issue(String username) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant now = Instant.now();
        RefreshToken token = new RefreshToken();
        token.setTokenHash(hash(rawToken));
        token.setUsername(username);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(Duration.ofDays(refreshExpirationDays)));
        repository.save(token);
        return rawToken;
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        RefreshToken token = find(rawToken);
        Instant now = Instant.now();

        if (token.getRevokedAt() != null) {
            log.warn("Revoked refresh token reused for user {}: revoking all their refresh tokens",
                    token.getUsername());
            revokeAll(token.getUsername(), now);
            throw new InvalidRefreshTokenException("Refresh token reused");
        }
        if (!token.isActive(now)) {
            throw new InvalidRefreshTokenException("Refresh token expired");
        }

        token.setRevokedAt(now);
        return new Rotation(token.getUsername(), issue(token.getUsername()));
    }

    /** Logout: thu hồi token này. Token không tồn tại thì bỏ qua (logout luôn thành công). */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
            }
        });
    }

    private RefreshToken find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException("Missing refresh token");
        }
        return repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Unknown refresh token"));
    }

    private void revokeAll(String username, Instant now) {
        for (RefreshToken token : repository.findByUsernameAndRevokedAtIsNull(username)) {
            token.setRevokedAt(now);
        }
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
