package com.example.andemo.controller;

import com.example.andemo.dto.ProductDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * API tìm sản phẩm theo barcode (US-05 Product Image Lookup).
 * Dữ liệu mock – thực tế sẽ lấy từ ERP/POS.
 */
@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductController {

    private final List<ProductDto> PRODUCTS = List.of(
            new ProductDto(
                    "8901234567890",
                    "Nước suối Vĩnh Hảo 500ml",
                    "Nước khoáng thiên nhiên",
                    "https://picsum.photos/seed/water500/400/400",
                    120
            ),
            new ProductDto(
                    "8934567890123",
                    "Mì Hảo Hảo tôm chua cay",
                    "Mì ăn liền hương vị tôm chua cay",
                    "https://picsum.photos/seed/noodle/400/400",
                    85
            ),
            new ProductDto(
                    "8851993123456",
                    "Sữa TH True Milk 1L",
                    "Sữa tươi tiệt trùng",
                    "https://picsum.photos/seed/milk/400/400",
                    40
            ),
            new ProductDto(
                    "8801234567890",
                    "Bánh quy Cosy",
                    "Bánh quy bơ sữa",
                    null,  // không có ảnh – test xử lý null imageUrl
                    60
            )
    );

    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<?> getByBarcode(@PathVariable String barcode) {
        Optional<ProductDto> found = PRODUCTS.stream()
                .filter(p -> p.getBarcode().equals(barcode))
                .findFirst();

        if (found.isPresent()) {
            return ResponseEntity.ok(found.get());
        }
        return ResponseEntity.status(404).body("Không tìm thấy sản phẩm với barcode: " + barcode);
    }
}
