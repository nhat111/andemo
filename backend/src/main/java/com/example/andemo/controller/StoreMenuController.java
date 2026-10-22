package com.example.andemo.controller;

import com.example.andemo.dto.MenuAccessResponse;
import com.example.andemo.dto.MenuDataDto;
import com.example.andemo.dto.ProductDto;
import com.example.andemo.service.MenuLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Màn hình nghiệp vụ của các menu (dữ liệu mock).
 * Backend tự chặn menu bị khóa (423) – không phụ thuộc vào việc app có ẩn nút hay không.
 */
@RestController
@RequestMapping("/api/store-menus")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StoreMenuController {

    private final MenuLockService menuLockService;

    private static final Map<String, List<List<String>>> MOCK_ROWS = Map.of(
            "ORDER", List.of(
                    List.of("PO-1001", "Nước suối Vĩnh Hảo 500ml", "48"),
                    List.of("PO-1002", "Mì Hảo Hảo tôm chua cay", "60")),
            "RETURN", List.of(
                    List.of("RT-2001", "Sữa TH True Milk 1L", "3")),
            "DISPOSAL", List.of(
                    List.of("DS-3001", "Bánh quy Cosy", "2")),
            "INVENTORY", List.of(
                    List.of("INV-01", "Nước suối Vĩnh Hảo 500ml", "120"),
                    List.of("INV-02", "Sữa TH True Milk 1L", "40"))
    );

    @GetMapping("/{menuCode}/data")
    public ResponseEntity<MenuDataDto> data(@PathVariable String menuCode, Authentication auth) {
        String storeId = menuLockService.resolveStoreId(auth.getName());
        menuLockService.assertAccessible(storeId, menuCode); // BLOCK + khóa → 423
        MenuAccessResponse access = menuLockService.checkAccess(storeId, menuCode);

        // Menu loại MASK (vd 원가): màn hình tra cứu sản phẩm, để trống Giá vốn & Tỷ suất lợi nhuận
        boolean isMaskType = menuLockService.getMenus(storeId).stream()
                .anyMatch(m -> m.getMenuCode().equals(menuCode)
                        && MenuLockService.LOCK_TYPE_MASK.equals(m.getLockType()));
        if (isMaskType) {
            boolean masked = access.isMasked();
            List<List<String>> rows = ProductController.PRODUCTS.stream()
                    .map(p -> masked ? p.masked() : p)
                    .map(this::toCostRow)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(new MenuDataDto(menuCode, masked,
                    List.of("Sản phẩm", "Giá bán", "Giá vốn", "Tỷ suất LN (%)"), rows));
        }

        return ResponseEntity.ok(new MenuDataDto(menuCode, false,
                List.of("Mã", "Sản phẩm", "Số lượng"),
                MOCK_ROWS.getOrDefault(menuCode, List.of())));
    }

    private List<String> toCostRow(ProductDto p) {
        return List.of(
                p.getName(),
                p.getSalePrice() != null ? String.valueOf(p.getSalePrice()) : "",
                p.getCostPrice() != null ? String.valueOf(p.getCostPrice()) : "",
                p.getMarginRate() != null ? String.valueOf(p.getMarginRate()) : ""
        );
    }
}
