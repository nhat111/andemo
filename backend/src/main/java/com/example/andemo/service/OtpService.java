package com.example.andemo.service;

import com.example.andemo.exception.MenuLockException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OTP 6 số cho thiết lập / reset PIN.
 * Demo: chưa tích hợp SMS → in OTP ra log. Bật app.otp.demo-mode thì OTP luôn là 123456.
 * - Phải yêu cầu mã trước khi nhập (không có mã đang hiệu lực → OTP_EXPIRED)
 * - Nhập sai đủ app.otp.max-attempts lần → hủy mã, phải yêu cầu mã mới
 * - Yêu cầu mã cách nhau tối thiểu app.otp.resend-seconds giây
 */
@Service
public class OtpService {

    private static final String DEMO_OTP = "123456";

    private final SecureRandom random = new SecureRandom();
    private final Map<String, OtpEntry> otps = new ConcurrentHashMap<>();
    // Tách riêng để vẫn giữ thời gian chờ khi mã đã bị hủy do nhập sai
    private final Map<String, Instant> lastIssuedAt = new ConcurrentHashMap<>();

    @Value("${app.otp.demo-mode:true}")
    private boolean demoMode;

    @Value("${app.otp.ttl-seconds:180}")
    private long ttlSeconds;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.otp.resend-seconds:60}")
    private long resendSeconds;

    private record OtpEntry(String code, Instant expiresAt, int failCount) {}

    /** Tạo OTP mới cho cửa hàng (ghi đè OTP cũ, reset số lần sai) */
    public void issue(String storeId) {
        Instant now = Instant.now();
        Instant last = lastIssuedAt.get(storeId);
        if (last != null && now.isBefore(last.plusSeconds(resendSeconds))) {
            long wait = Duration.between(now, last.plusSeconds(resendSeconds)).toSeconds() + 1;
            throw new MenuLockException(HttpStatus.TOO_MANY_REQUESTS, MenuLockException.OTP_TOO_FREQUENT,
                    "Vui lòng đợi " + wait + " giây trước khi yêu cầu mã mới");
        }

        String code = demoMode ? DEMO_OTP : String.format("%06d", random.nextInt(1_000_000));
        otps.put(storeId, new OtpEntry(code, now.plusSeconds(ttlSeconds), 0));
        lastIssuedAt.put(storeId, now);
        // TODO: gửi SMS cho chủ cửa hàng thay vì in log
        System.out.println(">>> [OTP] store=" + storeId + " code=" + code);
    }

    /** Kiểm tra OTP, đúng thì xóa luôn (dùng 1 lần). Sai → ném MenuLockException */
    public void verify(String storeId, String code) {
        check(storeId, code);
        otps.remove(storeId);
    }

    /** Chỉ kiểm tra OTP, không xóa (bước xác nhận OTP trước khi nhập mật khẩu mới). Sai → ném MenuLockException */
    public void check(String storeId, String code) {
        OtpEntry entry = otps.get(storeId);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            otps.remove(storeId);
            throw new MenuLockException(HttpStatus.BAD_REQUEST, MenuLockException.OTP_EXPIRED,
                    "Mã OTP đã hết hạn hoặc chưa được yêu cầu. Vui lòng yêu cầu mã mới");
        }
        if (entry.code().equals(code)) return;

        // Sai → tăng số lần sai (atomic theo từng cửa hàng)
        OtpEntry updated = otps.computeIfPresent(storeId, (k, e) -> e.failCount() + 1 >= maxAttempts
                ? null
                : new OtpEntry(e.code(), e.expiresAt(), e.failCount() + 1));
        if (updated == null) {
            throw new MenuLockException(HttpStatus.BAD_REQUEST, MenuLockException.OTP_ATTEMPTS_EXCEEDED,
                    "Nhập sai mã OTP quá " + maxAttempts + " lần. Vui lòng yêu cầu mã mới");
        }
        int remaining = maxAttempts - updated.failCount();
        throw new MenuLockException(HttpStatus.BAD_REQUEST, MenuLockException.OTP_INVALID,
                "Mã OTP không đúng. Còn " + remaining + " lần thử", remaining);
    }
}
