package com.example.restdemo.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API sản phẩm mẫu, dữ liệu giữ trong bộ nhớ (không cần DB):
 *   GET  /api/products?keyword=  danh sách (lọc theo tên)
 *   GET  /api/products/{code}    1 sản phẩm, không có thì 404
 *   POST /api/products           thêm mới: {"code":"...","name":"...","price":1000}
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final Map<String, Map<String, Object>> products =
            Collections.synchronizedMap(new LinkedHashMap<String, Map<String, Object>>());

    public ProductController() {
        add("8801234567890", "Kimbap tam giác", 1500);
        add("8809876543210", "Sữa chuối 240ml", 1700);
        add("8801111222333", "Nước suối 500ml", 900);
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "") String keyword) {
        List<Map<String, Object>> result = new ArrayList<>();
        synchronized (products) {
            for (Map<String, Object> p : products.values()) {
                if (((String) p.get("name")).toLowerCase().contains(keyword.toLowerCase())) {
                    result.add(p);
                }
            }
        }
        return result;
    }

    @GetMapping("/{code}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String code) {
        Map<String, Object> p = products.get(code);
        if (p == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("Không có sản phẩm " + code));
        }
        return ResponseEntity.ok(p);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> req) {
        Object code = req.get("code");
        Object name = req.get("name");
        Object price = req.get("price");
        if (!(code instanceof String) || ((String) code).isEmpty()
                || !(name instanceof String) || ((String) name).isEmpty()
                || !(price instanceof Number) || ((Number) price).longValue() < 0) {
            return ResponseEntity.badRequest().body(error("Cần code, name (chuỗi) và price (số >= 0)"));
        }
        if (products.containsKey(code)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("Đã có sản phẩm " + code));
        }
        Map<String, Object> p = add((String) code, (String) name, ((Number) price).longValue());
        return ResponseEntity.status(HttpStatus.CREATED).body(p);
    }

    private Map<String, Object> add(String code, String name, long price) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("code", code);
        p.put("name", name);
        p.put("price", price);
        products.put(code, p);
        return p;
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        return body;
    }
}
