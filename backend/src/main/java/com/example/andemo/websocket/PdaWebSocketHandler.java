package com.example.andemo.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * WebSocket /ws/pda?deviceId=… cho PDA nhận lệnh tìm.
 *
 * Xác thực: handshake là 1 request HTTP thường nên đi qua JwtFilter và SecurityConfig
 * (cần header Authorization: Bearer …). Kênh này chỉ dùng để server đẩy lệnh xuống;
 * PDA vẫn ack qua REST (POST /api/pda/alerts/{requestId}/ack).
 * Keepalive: client gửi ping frame (OkHttp pingInterval), Tomcat tự trả pong.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdaWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_DEVICE_ID = "deviceId";

    private final PdaSessionRegistry registry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String deviceId = session.getUri() == null ? null
                : UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("deviceId");
        if (deviceId == null || deviceId.isBlank()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Missing deviceId"));
            return;
        }
        session.getAttributes().put(ATTR_DEVICE_ID, deviceId);
        registry.register(deviceId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String deviceId = (String) session.getAttributes().get(ATTR_DEVICE_ID);
        if (deviceId != null) {
            registry.unregister(deviceId, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Transport error: {}", exception.getMessage());
    }
}
