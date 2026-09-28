package com.example.andemo.config;

import com.example.andemo.websocket.PdaWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final PdaWebSocketHandler pdaWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // PDA là app native, không gửi Origin của trình duyệt
        registry.addHandler(pdaWebSocketHandler, "/ws/pda").setAllowedOrigins("*");
    }
}
