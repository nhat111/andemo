package com.example.restdemo.pda;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PC bấm nút "Tìm PDA": POST /api/pda/find (không truyền deviceId, server tự chọn máy).
 * Trả về máy đã chọn để PC hiển thị "đã gửi tới PDA … (dùng lần cuối … bởi …)".
 */
@RestController
public class PdaFinderController {

    private final PdaFinderService finder;

    public PdaFinderController(PdaFinderService finder) {
        this.finder = finder;
    }

    @PostMapping("/api/pda/find")
    public ResponseEntity<Map<String, Object>> find(HttpServletRequest request) {
        LoginUser presser = DemoAuth.currentUser(request);
        Map<String, Object> body = new LinkedHashMap<>();
        if (presser == null) {
            body.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }
        PdaFinderService.Result result = finder.find(presser);

        List<Map<String, Object>> targets = new ArrayList<>();
        for (DeviceActivity d : result.getTargets()) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("uniqueId", d.getUniqueId());
            t.put("lastUserId", d.getUserId());
            t.put("lastActiveAt", d.getLastActiveAt().toString());
            t.put("loggedOut", d.isLoggedOut());
            targets.add(t);
            // TODO dự án thật: gửi lệnh tìm tới d.getUniqueId() ở đây (FCM / polling) và lưu requestId
            //      để PC theo dõi trạng thái "đã nhận".
        }
        body.put("rule", result.getRule());
        body.put("targets", targets);
        body.put("message", targets.isEmpty() ? "Không tìm thấy PDA"
                : "Đã gửi lệnh tìm tới " + targets.size() + " PDA");
        return ResponseEntity.ok(body);
    }
}
