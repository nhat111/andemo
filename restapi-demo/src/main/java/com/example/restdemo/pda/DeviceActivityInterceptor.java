package com.example.restdemo.pda;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Chạy tự động cho mọi API khớp mẫu đăng ký ở PdaWebConfig: KHÔNG phải sửa từng API.
 *
 * afterCompletion (sau khi API chạy xong) thay vì preHandle (trước):
 * chỉ tính request thành công + đã đăng nhập; request lỗi / chưa đăng nhập thì bỏ qua.
 * Interceptor không chặn, không đổi kết quả của API; chỉ ghi thêm "máy này vừa hoạt động".
 */
@Component
public class DeviceActivityInterceptor implements HandlerInterceptor {

    private final DeviceActivityService service;

    public DeviceActivityInterceptor(DeviceActivityService service) {
        this.service = service;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (ex != null || response.getStatus() >= 400) {
            return;
        }
        String uniqueId = DemoAuth.uniqueId(request);
        LoginUser user = DemoAuth.currentUser(request);
        if (uniqueId == null || user == null) {
            return; // không phải PDA (PC không gửi X-Unique-Id) hoặc chưa đăng nhập
        }
        service.touch(uniqueId, user);
    }
}
