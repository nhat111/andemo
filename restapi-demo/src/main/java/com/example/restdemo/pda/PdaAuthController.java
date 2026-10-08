package com.example.restdemo.pda;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Đăng nhập / đăng xuất BẢN DEMO (không kiểm tra mật khẩu) để thử luồng ghi nhận PDA.
 * Dự án thật: giữ API login / logout có sẵn, chỉ THÊM 2 dòng gọi deviceActivityService như dưới.
 */
@RestController
public class PdaAuthController {

    private final DeviceActivityService deviceActivityService;

    public PdaAuthController(DeviceActivityService deviceActivityService) {
        this.deviceActivityService = deviceActivityService;
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<Map<String, Object>> login(HttpServletRequest request) {
        LoginUser user = DemoAuth.currentUser(request);
        if (user == null) {
            return ResponseEntity.badRequest().body(message("Thiếu X-Store-Cd / X-User-Id"));
        }
        String uniqueId = DemoAuth.uniqueId(request);
        if (uniqueId != null) {
            deviceActivityService.touch(uniqueId, user); // ← thêm vào login thật, sau khi đăng nhập thành công
        }
        return ResponseEntity.ok(message("Đăng nhập: " + user.getStoreCd() + " / " + user.getUserId()));
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        String uniqueId = DemoAuth.uniqueId(request);
        if (uniqueId != null) {
            deviceActivityService.logout(uniqueId);       // ← thêm vào logout thật
        }
        return ResponseEntity.ok(message("Đã đăng xuất"));
    }

    private static Map<String, Object> message(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", text);
        return body;
    }
}
