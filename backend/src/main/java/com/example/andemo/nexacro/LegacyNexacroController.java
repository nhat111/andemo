package com.example.andemo.nexacro;

import com.example.andemo.dto.ProductDto;
import com.example.andemo.service.ProductCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GIẢ LẬP server Nexacro cũ (X-API): nhận và trả XML Dataset qua các URL *.do.
 * Dự án thật đã có sẵn các controller này; ở đây viết lại để demo cổng chuyển đổi
 * {@link NexacroGatewayController} chạy được mà không cần thư viện X-API.
 *
 * Không cần đăng nhập (giống form Nexacro gọi trực tiếp), chỉ trả dữ liệu mock.
 */
@RestController
@RequestMapping("/nexacro")
@RequiredArgsConstructor
public class LegacyNexacroController {

    private static final MediaType XML = new MediaType("text", "xml", java.nio.charset.StandardCharsets.UTF_8);

    private final ProductCatalog catalog;
    /** Tồn kho cộng thêm do các lần lưu nhập kho (demo 3), theo barcode */
    private final Map<String, Long> stockDelta = new ConcurrentHashMap<>();

    /** Demo 1 – transaction("search", "svc::product/search.do", "ds_search=ds_search", "ds_list=ds_list") */
    @PostMapping("/product/search.do")
    public ResponseEntity<String> searchProducts(@RequestBody(required = false) String body) {
        NexacroData in = parse(body);
        NxDataset dsSearch = in.dataset("ds_search");
        String keyword = dsSearch == null || dsSearch.getRows().isEmpty() ? null : dsSearch.getString(0, "keyword");

        NxDataset dsList = new NxDataset("ds_list")
                .addColumn("barcode", "string")
                .addColumn("name", "string")
                .addColumn("description", "string")
                .addColumn("imageUrl", "string")
                .addColumn("stockQuantity", "int");
        for (ProductDto p : catalog.search(keyword)) {
            Map<String, Object> row = dsList.addRow();
            row.put("barcode", p.getBarcode());
            row.put("name", p.getName());
            row.put("description", p.getDescription());
            row.put("imageUrl", p.getImageUrl());
            row.put("stockQuantity", currentStock(p));
        }
        return xml(NexacroData.success().addDataset(dsList));
    }

    /** Demo 2 – mã chung: 1 transaction trả về NHIỀU Dataset (ds_category, ds_unit) */
    @PostMapping("/code/list.do")
    public ResponseEntity<String> commonCodes(@RequestBody(required = false) String body) {
        NxDataset category = codes("ds_category", List.of(
                new String[]{"DRINK", "Đồ uống"}, new String[]{"FOOD", "Thực phẩm"}, new String[]{"DAIRY", "Sữa"}));
        NxDataset unit = codes("ds_unit", List.of(
                new String[]{"EA", "Cái"}, new String[]{"BOX", "Hộp"}, new String[]{"BTL", "Chai"}));
        NexacroData out = NexacroData.success().addDataset(category).addDataset(unit);
        out.getParams().put("codeVersion", "20260929");
        return xml(out);
    }

    /**
     * Demo 3 – lưu nhập kho: gửi ds_stock:U (chỉ dòng thay đổi, kèm rowtype).
     * insert: cộng qty; update: cộng (qty mới − qty gốc); delete: trừ qty gốc.
     * Lỗi nghiệp vụ trả ErrorCode &lt; 0 như X-API.
     */
    @PostMapping("/stock/save.do")
    public ResponseEntity<String> saveStock(@RequestBody(required = false) String body) {
        NexacroData in = parse(body);
        NxDataset ds = in.dataset("ds_stock");
        if (ds == null || ds.getRows().isEmpty()) {
            return xml(NexacroData.error(-1, "Không có dữ liệu cần lưu"));
        }

        // Kiểm tra hết trước khi ghi: 1 dòng lỗi thì không lưu dòng nào (giống 1 transaction DB)
        for (int i = 0; i < ds.getRows().size(); i++) {
            Map<String, Object> row = ds.getRows().get(i);
            String barcode = ds.getString(i, "barcode");
            if (barcode == null || catalog.findByBarcode(barcode).isEmpty()) {
                return xml(NexacroData.error(-1, "Dòng " + (i + 1) + ": không có sản phẩm " + barcode));
            }
            if (!"delete".equals(NxDataset.rowType(row)) && qty(row) <= 0) {
                return xml(NexacroData.error(-2, "Dòng " + (i + 1) + ": số lượng phải > 0"));
            }
        }

        int saved = 0;
        for (int i = 0; i < ds.getRows().size(); i++) {
            Map<String, Object> row = ds.getRows().get(i);
            long change = switch (NxDataset.rowType(row)) {
                case "insert" -> qty(row);
                case "update" -> qty(row) - qty(orgRow(row));
                case "delete" -> -qty(orgRow(row) != null ? orgRow(row) : row);
                default -> 0; // dòng không đổi: bỏ qua
            };
            if (change != 0 || !"normal".equals(NxDataset.rowType(row))) {
                stockDelta.merge(ds.getString(i, "barcode"), change, Long::sum);
                saved++;
            }
        }

        NxDataset result = new NxDataset("ds_result")
                .addColumn("barcode", "string")
                .addColumn("stockQuantity", "int");
        ds.getRows().stream().map(r -> (String) r.get("barcode")).distinct().forEach(barcode -> {
            Map<String, Object> row = result.addRow();
            row.put("barcode", barcode);
            row.put("stockQuantity", currentStock(catalog.findByBarcode(barcode).orElseThrow()));
        });
        NexacroData out = NexacroData.success().addDataset(result);
        out.getParams().put("savedCount", saved);
        return xml(out);
    }

    private long currentStock(ProductDto p) {
        return p.getStockQuantity() + stockDelta.getOrDefault(p.getBarcode(), 0L);
    }

    private static NxDataset codes(String id, List<String[]> items) {
        NxDataset ds = new NxDataset(id).addColumn("code", "string").addColumn("name", "string");
        for (String[] item : items) {
            Map<String, Object> row = ds.addRow();
            row.put("code", item[0]);
            row.put("name", item[1]);
        }
        return ds;
    }

    private static long qty(Map<String, Object> row) {
        if (row == null) {
            return 0;
        }
        Object v = row.get("qty");
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return v == null ? 0 : Long.parseLong(v.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> orgRow(Map<String, Object> row) {
        Object org = row.get(NxDataset.ORG_ROW);
        return org instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    private static NexacroData parse(String body) {
        return body == null || body.isBlank() ? new NexacroData() : NexacroXml.parse(body);
    }

    private static ResponseEntity<String> xml(NexacroData data) {
        return ResponseEntity.ok().contentType(XML).body(NexacroXml.write(data));
    }
}
