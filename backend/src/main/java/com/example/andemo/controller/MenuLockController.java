package com.example.andemo.controller;

import com.example.andemo.dto.LockRequest;
import com.example.andemo.dto.MenuAccessResponse;
import com.example.andemo.dto.MenuLockLogDto;
import com.example.andemo.dto.MenuLockStatusResponse;
import com.example.andemo.dto.MenuStatusDto;
import com.example.andemo.dto.OtpVerifyRequest;
import com.example.andemo.dto.PinSetupRequest;
import com.example.andemo.dto.UnlockRequest;
import com.example.andemo.service.MenuLockService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Khóa / mở khóa menu nhạy cảm (đặt hàng / trả hàng / hủy hàng / tồn kho / giá vốn) bằng mật khẩu 4 số.
 */
@RestController
@RequestMapping("/api/menu-lock")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MenuLockController {

    private final MenuLockService menuLockService;

    /** Trạng thái: đã cài PIN chưa, có đang bắt OTP không, danh sách menu + trạng thái khóa */
    @GetMapping("/status")
    public ResponseEntity<MenuLockStatusResponse> status(Authentication auth) {
        return ResponseEntity.ok(menuLockService.getStatus(storeOf(auth)));
    }

    /** Kiểm tra khi user bấm vào 1 menu */
    @GetMapping("/access/{menuCode}")
    public ResponseEntity<MenuAccessResponse> access(@PathVariable String menuCode, Authentication auth) {
        return ResponseEntity.ok(menuLockService.checkAccess(storeOf(auth), menuCode));
    }

    @PostMapping("/lock")
    public ResponseEntity<List<MenuStatusDto>> lock(@RequestBody LockRequest request,
                                                    Authentication auth, HttpServletRequest http) {
        return ResponseEntity.ok(menuLockService.lock(
                storeOf(auth), request.getMenuCodes(), auth.getName(), clientIp(http)));
    }

    @PostMapping("/unlock")
    public ResponseEntity<List<MenuStatusDto>> unlock(@RequestBody UnlockRequest request,
                                                      Authentication auth, HttpServletRequest http) {
        return ResponseEntity.ok(menuLockService.unlock(
                storeOf(auth), request.getMenuCode(), request.getPin(), auth.getName(), clientIp(http)));
    }

    /** Gửi OTP (demo: in ra log server, mặc định 123456) */
    @PostMapping("/otp/request")
    public ResponseEntity<?> requestOtp(Authentication auth) {
        menuLockService.requestOtp(storeOf(auth));
        return ResponseEntity.ok("Đã gửi mã OTP");
    }

    /** Bước xác nhận OTP: kiểm tra OTP trước khi nhập mật khẩu mới */
    @PostMapping("/otp/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody OtpVerifyRequest request, Authentication auth) {
        menuLockService.verifyOtp(storeOf(auth), request.getOtp());
        return ResponseEntity.ok("OTP hợp lệ");
    }

    /** Thiết lập lần đầu / Reset PIN: OTP + PIN mới + xác nhận PIN */
    @PostMapping("/pin")
    public ResponseEntity<?> setupPin(@RequestBody PinSetupRequest request,
                                      Authentication auth, HttpServletRequest http) {
        menuLockService.setupPin(storeOf(auth), request.getOtp(), request.getNewPin(),
                request.getConfirmPin(), auth.getName(), clientIp(http));
        return ResponseEntity.ok("Đã lưu mật khẩu");
    }

    @GetMapping("/logs")
    public ResponseEntity<List<MenuLockLogDto>> logs(Authentication auth) {
        return ResponseEntity.ok(menuLockService.getLogs(storeOf(auth)));
    }

    private String storeOf(Authentication auth) {
        return menuLockService.resolveStoreId(auth.getName());
    }

    /** Render chạy sau proxy → ưu tiên X-Forwarded-For */
    private String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
