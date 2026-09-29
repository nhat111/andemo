package com.example.andemo.service;

import com.example.andemo.dto.ProductDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Danh mục sản phẩm mock (thực tế lấy từ ERP/POS). Dùng chung cho API JSON (ProductController)
 * và server Nexacro giả lập (LegacyNexacroController): cùng nghiệp vụ, khác định dạng vào/ra.
 */
@Component
public class ProductCatalog {

    private static final List<ProductDto> PRODUCTS = List.of(
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

    public Optional<ProductDto> findByBarcode(String barcode) {
        return PRODUCTS.stream().filter(p -> p.getBarcode().equals(barcode)).findFirst();
    }

    /** Tìm theo tên hoặc barcode, không phân biệt hoa thường. Từ khoá trống: tất cả. */
    public List<ProductDto> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return PRODUCTS;
        }
        String k = keyword.trim().toLowerCase(Locale.ROOT);
        return PRODUCTS.stream()
                .filter(p -> p.getName().toLowerCase(Locale.ROOT).contains(k) || p.getBarcode().contains(k))
                .toList();
    }
}
