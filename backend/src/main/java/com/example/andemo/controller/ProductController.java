package com.example.andemo.controller;

import com.example.andemo.dto.ProductDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * API sản phẩm: tìm theo barcode (US-05 Product Image Lookup) và tra cứu theo từ khoá
 * (bài mẫu migrate Nexacro, docs/NEXACRO_MIGRATION_LAB.md).
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
            ),
            new ProductDto(
                    "8936036020151",
                    "Cà phê G7 3in1 (hộp 18 gói)",
                    "Cà phê hoà tan",
                    "https://picsum.photos/seed/coffee/400/400",
                    25
            ),
            new ProductDto(
                    "8934673573137",
                    "Nước tăng lực Sting dâu 330ml",
                    "Nước tăng lực hương dâu",
                    "https://picsum.photos/seed/sting/400/400",
                    200
            ),
            new ProductDto(
                    "8935049510864",
                    "Dầu ăn Neptune 1L",
                    "Dầu ăn cao cấp",
                    "https://picsum.photos/seed/oil/400/400",
                    0
            )
    );

    /**
     * Tra cứu sản phẩm theo từ khoá (tên hoặc barcode, không phân biệt hoa thường).
     * Từ khoá trống: trả về tất cả.
     *
     * Tương đương transaction "search" của form Nexacro mẫu
     * (nexacro-sample/frm_product_search.xfdl): ds_search → query param, ds_list → JSON array.
     */
    @GetMapping
    public List<ProductDto> search(@RequestParam(required = false) String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return PRODUCTS;
        }
        String k = keyword.trim().toLowerCase(Locale.ROOT);
        return PRODUCTS.stream()
                .filter(p -> p.getName().toLowerCase(Locale.ROOT).contains(k) || p.getBarcode().contains(k))
                .toList();
    }

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
