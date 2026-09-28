package com.example.andemo.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giữ kết nối WebSocket đang mở của từng PDA: deviceId → session.
 *
 * Chỉ đúng khi backend chạy 1 instance. Chạy nhiều instance thì PDA có thể kết nối vào
 * instance khác với instance tạo lệnh, cần Redis pub/sub hoặc message broker để chuyển lệnh.
 */
@Slf4j
@Component
public class PdaSessionRegistry {

    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 64 * 1024;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(String deviceId, WebSocketSession session) {
        // WebSocketSession không an toàn khi nhiều thread cùng gửi; decorator tự xếp hàng
        WebSocketSession safeSession =
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
        WebSocketSession previous = sessions.put(deviceId, safeSession);
        if (previous != null && previous.isOpen()) {
            // PDA kết nối lại khi server chưa biết kết nối cũ đã chết: đóng kết nối cũ
            closeQuietly(previous, CloseStatus.NORMAL.withReason("Replaced by new connection"));
        }
        log.info("PDA connected: {} (online: {})", deviceId, sessions.size());
    }

    public void unregister(String deviceId, WebSocketSession session) {
        // Chỉ xóa nếu đúng session này: session cũ đóng muộn không được xóa session mới
        sessions.computeIfPresent(deviceId, (id, current) ->
                current.getId().equals(session.getId()) ? null : current);
        log.info("PDA disconnected: {} (online: {})", deviceId, sessions.size());
    }

    public Set<String> onlineDeviceIds() {
        return new TreeSet<>(sessions.keySet());
    }

    /**
     * Gửi tới 1 PDA, hoặc tới mọi PDA đang online nếu deviceId là null.
     *
     * @return số PDA đã gửi được. PDA offline sẽ tự lấy lệnh qua API pending khi kết nối lại.
     */
    public int send(String deviceId, String payload) {
        if (deviceId == null) {
            int sent = 0;
            for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
                if (sendTo(entry.getKey(), entry.getValue(), payload)) {
                    sent++;
                }
            }
            return sent;
        }
        WebSocketSession session = sessions.get(deviceId);
        return session != null && sendTo(deviceId, session, payload) ? 1 : 0;
    }

    private boolean sendTo(String deviceId, WebSocketSession session, String payload) {
        if (!session.isOpen()) {
            return false;
        }
        try {
            session.sendMessage(new TextMessage(payload));
            return true;
        } catch (IOException | RuntimeException e) {
            log.warn("Send to PDA {} failed: {}", deviceId, e.getMessage());
            return false;
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            log.debug("Close failed: {}", e.getMessage());
        }
    }
}
