package com.example.andemo.websocket;

import com.example.andemo.service.PdaDeviceService;
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
    private final PdaDeviceService deviceService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        var params = session.getUri() == null ? null
                : UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams();
        String deviceId = params == null ? null : params.getFirst("deviceId");
        if (deviceId == null || deviceId.isBlank()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Missing deviceId"));
            return;
        }
        session.getAttributes().put(ATTR_DEVICE_ID, deviceId);
        registry.register(deviceId, session);
        // deviceName gửi dạng query param nên đã được mã hóa URL: giải mã trước khi lưu
        String deviceName = params.getFirst("deviceName");
        deviceService.recordContact(deviceId,
                deviceName == null ? null : java.net.URLDecoder.decode(deviceName, java.nio.charset.StandardCharsets.UTF_8),
                session.getPrincipal() == null ? null : session.getPrincipal().getName());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String deviceId = (String) session.getAttributes().get(ATTR_DEVICE_ID);
        if (deviceId != null) {
            registry.unregister(deviceId, session);
            // Ghi lại thời điểm liên lạc cuối để màn hình requester hiện "offline, liên lạc x phút trước"
            deviceService.recordContact(deviceId, null, null);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Transport error: {}", exception.getMessage());
    }
}
