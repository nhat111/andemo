package com.example.andemo.service;

import com.example.andemo.dto.PdaDeviceDto;
import com.example.andemo.entity.PdaDevice;
import com.example.andemo.repository.PdaDeviceRepository;
import com.example.andemo.websocket.PdaSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PdaDeviceService {

    // PDA polling hỏi mỗi ~30 giây: quá 3 chu kỳ không liên lạc thì coi là offline
    private static final Duration ONLINE_WINDOW = Duration.ofSeconds(90);
    private static final int MAX_NAME_LENGTH = 100;

    private final PdaDeviceRepository repository;
    private final PdaSessionRegistry sessionRegistry;

    /** PDA vừa liên lạc: poll pending, kết nối hoặc ngắt WebSocket. */
    @Transactional
    public void recordContact(String deviceId, String deviceName, String username) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        PdaDevice device = repository.findById(deviceId).orElseGet(() -> {
            PdaDevice created = new PdaDevice();
            created.setDeviceId(deviceId);
            return created;
        });
        if (deviceName != null && !deviceName.isBlank()) {
            device.setDeviceName(truncate(deviceName));
        }
        if (username != null) {
            device.setUsername(username);
        }
        device.setLastSeenAt(Instant.now());
        repository.save(device);
    }

    @Transactional(readOnly = true)
    public List<PdaDeviceDto> list() {
        Instant now = Instant.now();
        Set<String> connected = sessionRegistry.onlineDeviceIds();
        return repository.findAllByOrderByLastSeenAtDesc().stream()
                .map(device -> {
                    boolean isConnected = connected.contains(device.getDeviceId());
                    long seconds = isConnected ? 0
                            : Math.max(0, Duration.between(device.getLastSeenAt(), now).getSeconds());
                    boolean online = isConnected || seconds < ONLINE_WINDOW.getSeconds();
                    return new PdaDeviceDto(device.getDeviceId(), device.getDeviceName(), device.getUsername(),
                            device.getLastSeenAt(), seconds, online, isConnected, device.getFcmToken() != null);
                })
                .toList();
    }

    /**
     * PDA gửi FCM token lên (sau login, hoặc khi Firebase cấp token mới). Token rỗng = xóa.
     * Một token chỉ thuộc 1 PDA: nếu token đang gắn với máy khác (cài lại app, đổi deviceId) thì gỡ ở máy đó.
     */
    @Transactional
    public void saveFcmToken(String deviceId, String deviceName, String username, String fcmToken) {
        String token = fcmToken == null || fcmToken.isBlank() ? null : fcmToken;
        if (token != null) {
            for (PdaDevice other : repository.findByFcmToken(token)) {
                if (!other.getDeviceId().equals(deviceId)) {
                    other.setFcmToken(null);
                }
            }
        }
        recordContact(deviceId, deviceName, username);
        repository.findById(deviceId).ifPresent(device -> {
            device.setFcmToken(token);
            device.setFcmTokenUpdatedAt(Instant.now());
        });
    }

    /** FCM báo token không còn hợp lệ (app bị gỡ, token bị xóa khi logout…). */
    @Transactional
    public void removeFcmToken(String fcmToken) {
        for (PdaDevice device : repository.findByFcmToken(fcmToken)) {
            device.setFcmToken(null);
            device.setFcmTokenUpdatedAt(Instant.now());
        }
    }

    /** FCM token của 1 PDA, hoặc của mọi PDA khi deviceId là null. */
    @Transactional(readOnly = true)
    public List<String> fcmTokens(String deviceId) {
        if (deviceId == null) {
            return repository.findByFcmTokenIsNotNull().stream().map(PdaDevice::getFcmToken).toList();
        }
        return repository.findById(deviceId)
                .map(PdaDevice::getFcmToken)
                .map(List::of)
                .orElse(List.of());
    }

    private static String truncate(String value) {
        return value.length() <= MAX_NAME_LENGTH ? value : value.substring(0, MAX_NAME_LENGTH);
    }
}
