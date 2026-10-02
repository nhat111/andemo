package com.example.restdemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** In ra môi trường đang chạy khi khởi động, để biết chắc đã chọn đúng resources-<profile>. */
@Component
public class StartupLog implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupLog.class);

    @Value("${app.env}")
    private String env;

    @Value("${app.db-url}")
    private String dbUrl;

    @Override
    public void run(ApplicationArguments args) {
        log.info("==> Môi trường: {} | DB: {}", env, dbUrl);
        if ("ops".equals(env)) {
            log.warn("==> ĐANG CHẠY CẤU HÌNH OPS (PRODUCTION) TRÊN MÁY NÀY. Dừng lại nếu không chủ ý!");
        }
    }
}
