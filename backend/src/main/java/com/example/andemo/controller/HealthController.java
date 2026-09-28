package com.example.andemo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

@RestController
public class HealthController {

    /** Health check cho Render (render.yaml: healthCheckPath). Không cần đăng nhập. */
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    /** Trang chủ → web quản lý PDA Finder. */
    @GetMapping("/")
    public RedirectView home() {
        return new RedirectView("/pda-finder.html");
    }
}
