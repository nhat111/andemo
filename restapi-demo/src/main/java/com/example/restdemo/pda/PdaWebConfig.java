package com.example.restdemo.pda;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Đăng ký interceptor 1 lần cho mọi API.
 * Dự án đã có class implements WebMvcConfigurer thì thêm addInterceptor vào class đó (không tạo class thứ 2).
 * Spring MVC dùng XML (eGovFrame): khai báo trong dispatcher-servlet.xml, xem README.
 */
@Configuration
public class PdaWebConfig implements WebMvcConfigurer {

    private final DeviceActivityInterceptor deviceActivityInterceptor;

    public PdaWebConfig(DeviceActivityInterceptor deviceActivityInterceptor) {
        this.deviceActivityInterceptor = deviceActivityInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(deviceActivityInterceptor)
                .addPathPatterns("/api/**")
                // login: tự gọi touch sau khi đăng nhập thành công
                // logout: nếu không loại trừ, interceptor chạy SAU logout sẽ MERGE lại và xoá mất LOGOUT_AT
                .excludePathPatterns("/api/auth/login", "/api/auth/logout");
    }
}
