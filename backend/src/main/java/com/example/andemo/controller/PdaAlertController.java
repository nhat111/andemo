package com.example.andemo.controller;

import com.example.andemo.dto.CreatePdaAlertRequest;
import com.example.andemo.dto.PdaAlertAckRequest;
import com.example.andemo.dto.PdaAlertCommand;
import com.example.andemo.entity.PdaAlert;
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
 * - ADMIN (quản lý): tạo lệnh, xem lịch sử, xem PDA đang online
 * - PDA (user đã login): lấy lệnh pending, ack
 */
@RestController
@RequestMapping("/api/pda")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PdaAlertController {

    private final PdaFinderService pdaFinderService;
    private final PdaSessionRegistry sessionRegistry;

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

    @GetMapping("/alerts/pending")
    public List<PdaAlertCommand> pending(@RequestParam String deviceId) {
        return pdaFinderService.pending(deviceId);
    }

    @PostMapping("/alerts/{requestId}/ack")
    public ResponseEntity<Void> ack(@PathVariable String requestId, @RequestBody PdaAlertAckRequest request) {
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
