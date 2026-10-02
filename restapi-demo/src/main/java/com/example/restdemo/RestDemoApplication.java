package com.example.restdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Điểm bắt đầu của app.
 * - Chạy trực tiếp (bấm Run / gradlew bootRun): dùng hàm main, Tomcat nhúng.
 * - Deploy file .war vào Tomcat ngoài: Tomcat gọi configure(...) (vì kế thừa SpringBootServletInitializer).
 */
@SpringBootApplication
public class RestDemoApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(RestDemoApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(RestDemoApplication.class);
    }
}
