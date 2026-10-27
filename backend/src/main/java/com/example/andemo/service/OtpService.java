package com.example.andemo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OTP 6 số cho thiết lập / reset PIN.
 * Demo: chưa tích hợp SMS → in OTP ra log. Bật app.otp.demo-mode thì OTP luôn là 123456.
 */
@Service
public class OtpService {

    private static final String DEMO_OTP = "123456";

    private final SecureRandom random = new SecureRandom();
    private final Map<String, OtpEntry> otps = new ConcurrentHashMap<>();

    @Value("${app.otp.demo-mode:true}")
    private boolean demoMode;

    @Value("${app.otp.ttl-seconds:180}")
    private long ttlSeconds;

    private record OtpEntry(String code, Instant expiresAt) {}

    /** Tạo OTP mới cho cửa hàng (ghi đè OTP cũ) */
    public void issue(String storeId) {
        String code = demoMode ? DEMO_OTP : String.format("%06d", random.nextInt(1_000_000));
        otps.put(storeId, new OtpEntry(code, Instant.now().plusSeconds(ttlSeconds)));
        // TODO: gửi SMS cho chủ cửa hàng thay vì in log
        System.out.println(">>> [OTP] store=" + storeId + " code=" + code);
    }

    /** Kiểm tra OTP, đúng thì xóa luôn (dùng 1 lần) */
    public boolean verify(String storeId, String code) {
        if (!check(storeId, code)) return false;
        otps.remove(storeId);
        return true;
    }

    /** Chỉ kiểm tra OTP, không xóa (bước xác nhận OTP trước khi nhập mật khẩu mới) */
    public boolean check(String storeId, String code) {
        if (code == null) return false;

        OtpEntry entry = otps.get(storeId);
        if (entry == null && demoMode) {
            // Demo: cho phép dùng 123456 kể cả khi chưa bấm "Gửi OTP"
            return DEMO_OTP.equals(code);
        }
        return entry != null && !Instant.now().isAfter(entry.expiresAt()) && entry.code().equals(code);
    }
}
