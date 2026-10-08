package com.example.restdemo.pda;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Ghi nhận "PDA vừa hoạt động" (gọi từ DeviceActivityInterceptor và API đăng nhập).
 * - Chặn trong RAM: mỗi (máy, cửa hàng, người dùng) tối đa 1 lần ghi DB / min-interval-ms.
 * - Lỗi ghi DB KHÔNG được làm hỏng API chính: chỉ log cảnh báo.
 */
@Service
public class DeviceActivityService {

    private static final Logger log = LoggerFactory.getLogger(DeviceActivityService.class);

    private final DeviceActivityMapper mapper;
    private final long minIntervalMs;
    /** key "uniqueId|store|user" → thời điểm ghi DB gần nhất */
    private final ConcurrentHashMap<String, Long> lastWrite = new ConcurrentHashMap<>();

    public DeviceActivityService(DeviceActivityMapper mapper,
                                 @Value("${pda.activity.min-interval-ms:60000}") long minIntervalMs) {
        this.mapper = mapper;
        this.minIntervalMs = minIntervalMs;
    }

    public void touch(String uniqueId, LoginUser user) {
        // Đổi cửa hàng / người dùng → key khác → ghi ngay, không chờ hết khoảng chặn
        String key = uniqueId + "|" + user.getStoreCd() + "|" + user.getUserId();
        long now = System.currentTimeMillis();
        Long prev = lastWrite.get(key);
        if (prev != null && now - prev < minIntervalMs) {
            return; // vừa ghi rồi: không gọi DB
        }
        lastWrite.put(key, now);
        try {
            mapper.touch(uniqueId, user.getStoreCd(), user.getUserId());
        } catch (RuntimeException e) {
            lastWrite.remove(key); // lần sau thử ghi lại
            log.warn("Không ghi được hoạt động PDA: device={}, store={}", uniqueId, user.getStoreCd(), e);
        }
    }

    /** Đăng xuất: chỉ ghi LOGOUT_AT (máy vẫn được tìm). Xoá chặn để lần dùng tiếp theo ghi ngay. */
    public void logout(String uniqueId) {
        lastWrite.keySet().removeIf(k -> k.startsWith(uniqueId + "|"));
        mapper.logout(uniqueId);
    }
}
