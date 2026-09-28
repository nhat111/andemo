package com.example.andemo.controller;

import com.example.andemo.dto.CreatePdaAlertRequest;
import com.example.andemo.dto.FcmTokenRequest;
import com.example.andemo.dto.PdaAlertAckRequest;
import com.example.andemo.dto.PdaAlertCommand;
import com.example.andemo.dto.PdaDeviceDto;
import com.example.andemo.entity.PdaAlert;
import com.example.andemo.service.PdaDeviceService;
import com.example.andemo.service.PdaFinderService;
import com.example.andemo.websocket.PdaSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * API PDA Finder.
 * - ADMIN (quản lý, web /pda-finder.html hoặc nút "Tìm PDA" trong app):
 *   danh sách PDA, tạo lệnh, xem trạng thái 1 lệnh, lịch sử
 * - PDA (user đã login): lấy lệnh pending, ack
 */
@RestController
@RequestMapping("/api/pda")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PdaAlertController {

    private final PdaFinderService pdaFinderService;
    private final PdaSessionRegistry sessionRegistry;
    private final PdaDeviceService pdaDeviceService;

    @PostMapping("/alerts")
    public ResponseEntity<PdaAlert> create(@RequestBody CreatePdaAlertRequest request,
                                           Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        PdaAlert alert = pdaFinderService.create(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(alert);
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<PdaAlert>> list(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(pdaFinderService.findAll());
    }

    @GetMapping("/alerts/{requestId}")
    public ResponseEntity<PdaAlert> get(@PathVariable String requestId, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return pdaFinderService.find(requestId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** PDA hỏi lệnh; đồng thời ghi nhận PDA vừa liên lạc (tên máy, user đang login). */
    @GetMapping("/alerts/pending")
    public List<PdaAlertCommand> pending(@RequestParam String deviceId,
                                         @RequestParam(required = false) String deviceName,
                                         Authentication authentication) {
        pdaDeviceService.recordContact(deviceId, deviceName, authentication.getName());
        return pdaFinderService.pending(deviceId);
    }

    /** PDA đăng ký FCM token (token rỗng / null = hủy đăng ký). */
    @PutMapping("/devices/{deviceId}/fcm-token")
    public ResponseEntity<Void> updateFcmToken(@PathVariable String deviceId, @RequestBody FcmTokenRequest request,
                                               Authentication authentication) {
        pdaDeviceService.saveFcmToken(deviceId, request.getDeviceName(), authentication.getName(),
                request.getFcmToken());
        return ResponseEntity.noContent().build();
    }

    /** PDA server biết, liên lạc gần nhất đứng đầu. */
    @GetMapping("/devices")
    public ResponseEntity<List<PdaDeviceDto>> devices(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(pdaDeviceService.list());
    }

    @PostMapping("/alerts/{requestId}/ack")
    public ResponseEntity<Void> ack(@PathVariable String requestId, @RequestBody PdaAlertAckRequest request,
                                    Authentication authentication) {
        // PDA nhận qua FCM không poll: ack cũng là một lần liên lạc
        pdaDeviceService.recordContact(request.getDeviceId(), null, authentication.getName());
        return pdaFinderService.ack(requestId, request.getDeviceId(), request.getStatus())
                .<ResponseEntity<Void>>map(alert -> ResponseEntity.noContent().build())
                .orElse(ResponseEntity.notFound().build());
    }

    /** PDA đang giữ kết nối WebSocket. */
    @GetMapping("/devices/online")
    public ResponseEntity<Set<String>> online(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(sessionRegistry.onlineDeviceIds());
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ADMIN".equals(authority.getAuthority()));
    }
}
