package com.example.andemo.controller;

import com.example.andemo.dto.ItemDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/items")
@CrossOrigin(origins = "*")
public class ItemController {

    // Danh sách mẫu (thực tế sẽ lấy từ DB)
    private final List<ItemDto> ALL_ITEMS = List.of(
            new ItemDto(1L, "Sản phẩm A", "Ai cũng thấy được", false),
            new ItemDto(2L, "Sản phẩm B", "Ai cũng thấy được", false),
            new ItemDto(3L, "Báo cáo doanh thu", "Chỉ ADMIN mới thấy", true),
            new ItemDto(4L, "Quản lý user", "Chỉ ADMIN mới thấy", true),
            new ItemDto(5L, "Cấu hình hệ thống", "Chỉ ADMIN mới thấy", true)
    );

    @GetMapping
    public ResponseEntity<List<ItemDto>> getItems(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("USER");

        List<ItemDto> result;

        if ("ADMIN".equals(role)) {
            // ADMIN thấy hết
            result = new ArrayList<>(ALL_ITEMS);
        } else {
            // USER chỉ thấy item không phải adminOnly
            result = ALL_ITEMS.stream()
                    .filter(item -> !item.isAdminOnly())
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(result);
    }
}
