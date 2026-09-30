package com.example.andemo.controller;

import com.example.andemo.dto.ProductDto;
import com.example.andemo.service.ProductCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * API sản phẩm: tìm theo barcode (US-05 Product Image Lookup) và tra cứu theo từ khoá
 * (bài mẫu migrate Nexacro, docs/nexacro-migration/MIGRATION_LAB.md).
 * Dữ liệu mock trong ProductCatalog – thực tế sẽ lấy từ ERP/POS.
 */
@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ProductController {

    private final ProductCatalog catalog;

    /**
     * Tra cứu sản phẩm theo từ khoá (tên hoặc barcode, không phân biệt hoa thường).
     * Từ khoá trống: trả về tất cả.
     *
     * Tương đương transaction "search" của form Nexacro mẫu
     * (nexacro-sample/frm_product_search.xfdl): ds_search → query param, ds_list → JSON array.
     */
    @GetMapping
    public List<ProductDto> search(@RequestParam(required = false) String keyword) {
        return catalog.search(keyword);
    }

    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<?> getByBarcode(@PathVariable String barcode) {
        Optional<ProductDto> found = catalog.findByBarcode(barcode);

        if (found.isPresent()) {
            return ResponseEntity.ok(found.get());
        }
        return ResponseEntity.status(404).body("Không tìm thấy sản phẩm với barcode: " + barcode);
    }
}
