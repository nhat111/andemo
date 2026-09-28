package com.example.andemo.controller;

import com.example.andemo.push.PushSender;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final PushSender pushSender;

    /**
     * Health check cho Render (render.yaml: healthCheckPath). Không cần đăng nhập.
     * "fcm": server đã cấu hình Firebase chưa (web quản lý hiển thị).
     */
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "fcm", pushSender.isEnabled());
    }

    /** Trang chủ → web quản lý PDA Finder. */
    @GetMapping("/")
    public RedirectView home() {
        return new RedirectView("/pda-finder.html");
    }
}
