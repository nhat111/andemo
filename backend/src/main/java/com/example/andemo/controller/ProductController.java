package com.example.andemo.controller;

import com.example.andemo.dto.ProductDto;
import com.example.andemo.service.MenuLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * API tìm sản phẩm theo barcode (US-05 Product Image Lookup).
 * Dữ liệu mock – thực tế sẽ lấy từ ERP/POS.
 * Khi menu 원가 (COST) bị khóa → trả null cho costPrice / marginRate (tránh leak qua F12 Network tab).
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProductController {

    public static final String COST_MENU_CODE = "COST";

    private final MenuLockService menuLockService;

    public static final List<ProductDto> PRODUCTS = List.of(
            new ProductDto(
                    "8901234567890",
                    "Nước suối Vĩnh Hảo 500ml",
                    "Nước khoáng thiên nhiên",
                    "https://picsum.photos/seed/water500/400/400",
                    120,
                    6000, 4200, 30.0
            ),
            new ProductDto(
                    "8934567890123",
                    "Mì Hảo Hảo tôm chua cay",
                    "Mì ăn liền hương vị tôm chua cay",
                    "https://picsum.photos/seed/noodle/400/400",
                    85,
                    4500, 3600, 20.0
            ),
            new ProductDto(
                    "8851993123456",
                    "Sữa TH True Milk 1L",
                    "Sữa tươi tiệt trùng",
                    "https://picsum.photos/seed/milk/400/400",
                    40,
                    38000, 31000, 18.4
            ),
            new ProductDto(
                    "8801234567890",
                    "Bánh quy Cosy",
                    "Bánh quy bơ sữa",
                    null,  // không có ảnh – test xử lý null imageUrl
                    60,
                    25000, 19000, 24.0
            )
    );

    @GetMapping
    public ResponseEntity<List<ProductDto>> getAll(Authentication authentication) {
        boolean mask = isCostLocked(authentication);
        return ResponseEntity.ok(PRODUCTS.stream()
                .map(p -> mask ? p.masked() : p)
                .collect(Collectors.toList()));
    }

    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<?> getByBarcode(@PathVariable String barcode, Authentication authentication) {
        Optional<ProductDto> found = PRODUCTS.stream()
                .filter(p -> p.getBarcode().equals(barcode))
                .findFirst();

        if (found.isPresent()) {
            ProductDto product = found.get();
            return ResponseEntity.ok(isCostLocked(authentication) ? product.masked() : product);
        }
        return ResponseEntity.status(404).body("Không tìm thấy sản phẩm với barcode: " + barcode);
    }

    private boolean isCostLocked(Authentication authentication) {
        String storeId = menuLockService.resolveStoreId(authentication.getName());
        return menuLockService.isLocked(storeId, COST_MENU_CODE);
    }
}
