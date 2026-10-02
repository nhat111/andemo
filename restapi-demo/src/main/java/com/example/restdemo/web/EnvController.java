package com.example.restdemo.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** GET /api/env: xem app đang dùng cấu hình môi trường nào. */
@RestController
public class EnvController {

    @Value("${app.env}")
    private String env;

    @Value("${app.message}")
    private String message;

    @GetMapping("/api/env")
    public Map<String, String> env() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("env", env);
        body.put("message", message);
        return body;
    }
}
