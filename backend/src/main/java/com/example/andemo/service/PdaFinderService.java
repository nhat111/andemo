package com.example.andemo.service;

import com.example.andemo.dto.CreatePdaAlertRequest;
import com.example.andemo.dto.PdaAlertCommand;
import com.example.andemo.entity.PdaAlert;
import com.example.andemo.entity.PdaAlertStatus;
import com.example.andemo.push.PushSender;
import com.example.andemo.repository.PdaAlertRepository;
import com.example.andemo.websocket.PdaSessionRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tạo lệnh tìm PDA, đẩy qua WebSocket, và cung cấp danh sách pending cho polling / bắt kịp.
 *
 * Bảng pda_alert là nguồn dữ liệu gốc. WebSocket chỉ là "chuông cửa": PDA đang offline
 * không nhận được qua WebSocket, nhưng khi kết nối lại sẽ tự gọi API pending.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdaFinderService {

    private static final long DEFAULT_TTL_SECONDS = 120;
    private static final long MAX_TTL_SECONDS = 3600;
    private static final String DEFAULT_MESSAGE = "PDA đang được tìm kiếm bởi quản lý";

    private final PdaAlertRepository repository;
    private final PdaSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final PdaDeviceService deviceService;
    private final PushSender pushSender;

    @Transactional
    public PdaAlert create(CreatePdaAlertRequest request, String requestedBy) {
        long ttl = request.getTtlSeconds() == null ? DEFAULT_TTL_SECONDS
                : Math.max(1, Math.min(request.getTtlSeconds(), MAX_TTL_SECONDS));
        Instant now = Instant.now();

        PdaAlert alert = new PdaAlert();
        alert.setRequestId("REQ-" + UUID.randomUUID());
        alert.setDeviceId(blankToNull(request.getDeviceId()));
        alert.setStoreCode(request.getStoreCode());
        alert.setMessage(blankToNull(request.getMessage()) == null ? DEFAULT_MESSAGE : request.getMessage());
        alert.setRequestedBy(requestedBy);
        alert.setCreatedAt(now);
        alert.setExpiresAt(now.plusSeconds(ttl));
        alert.setStatus(PdaAlertStatus.SENT);
        repository.save(alert);

        int pushed = sessionRegistry.send(alert.getDeviceId(), toJson(PdaAlertCommand.from(alert)));
        int viaFcm = sendFcm(alert);
        log.info("Alert {} created by {} for {}, pushed via WebSocket to {} PDA(s), via FCM to {} PDA(s)",
                alert.getRequestId(), requestedBy,
                alert.getDeviceId() == null ? "all PDAs" : alert.getDeviceId(), pushed, viaFcm);
        return alert;
    }

    /**
     * Gửi qua FCM tới các PDA đã đăng ký token. Cùng lệnh có thể tới PDA qua cả WebSocket / poll:
     * app chống trùng theo requestId.
     */
    private int sendFcm(PdaAlert alert) {
        if (!pushSender.isEnabled()) {
            return 0;
        }
        // FCM data message chỉ nhận giá trị String, không nhận null
        Map<String, String> data = new HashMap<>();
        data.put("type", "PDA_FINDER_ALERT");
        data.put("requestId", alert.getRequestId());
        data.put("message", alert.getMessage());
        data.put("expiresAt", alert.getExpiresAt().toString());
        if (alert.getStoreCode() != null) {
            data.put("storeCode", alert.getStoreCode());
        }
        Duration ttl = Duration.between(Instant.now(), alert.getExpiresAt());

        List<String> tokens = deviceService.fcmTokens(alert.getDeviceId());
        for (String token : tokens) {
            pushSender.send(token, data, ttl, () -> deviceService.removeFcmToken(token));
        }
        return tokens.size();
    }

    /** Lệnh chưa được PDA xác nhận và chưa hết hạn (theo giờ server). */
    @Transactional(readOnly = true)
    public List<PdaAlertCommand> pending(String deviceId) {
        return repository.findByStatusAndExpiresAtAfter(PdaAlertStatus.SENT, Instant.now()).stream()
                .filter(alert -> alert.isFor(deviceId))
                .map(PdaAlertCommand::from)
                .toList();
    }

    /**
     * @return empty nếu không có lệnh đó (hoặc không dành cho thiết bị này)
     */
    @Transactional
    public Optional<PdaAlert> ack(String requestId, String deviceId, PdaAlertStatus status) {
        Optional<PdaAlert> found = repository.findById(requestId).filter(alert -> alert.isFor(deviceId));
        found.ifPresent(alert -> {
            if (status == null || !alert.getStatus().canTransitionTo(status)) {
                // Ack lặp lại hoặc đến muộn: bỏ qua, vẫn trả thành công để PDA không gửi lại
                return;
            }
            Instant now = Instant.now();
            if (status == PdaAlertStatus.DELIVERED) {
                alert.setDeliveredAt(now);
            } else if (status.isFinal()) {
                if (alert.getDeliveredAt() == null) {
                    alert.setDeliveredAt(now);
                }
                alert.setFinishedAt(now);
            }
            alert.setStatus(status);
            log.info("Alert {} is now {} (device {})", requestId, status, deviceId);
        });
        return found;
    }

    @Transactional(readOnly = true)
    public Optional<PdaAlert> find(String requestId) {
        return repository.findById(requestId);
    }

    @Transactional(readOnly = true)
    public List<PdaAlert> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize alert", e);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
