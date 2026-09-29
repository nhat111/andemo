package com.example.andemo.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Body gửi lên cổng /api/nx/** (backend đổi sang XML Dataset cho server Nexacro cũ).
 * Tương đương tham số in-dataset + argument của transaction():
 *
 * <pre>
 * NxRequest req = new NxRequest()
 *         .param("mode", "A")
 *         .row("ds_search").put("keyword", "sữa");
 * </pre>
 */
public class NxRequest {

    private final Map<String, Object> params = new LinkedHashMap<>();
    private final Map<String, List<Map<String, Object>>> datasets = new LinkedHashMap<>();

    public NxRequest param(String name, Object value) {
        params.put(name, value);
        return this;
    }

    /** Thêm 1 dòng (trạng thái bình thường) vào Dataset; trả về dòng để put các cột. */
    public Map<String, Object> row(String datasetId) {
        Map<String, Object> row = new LinkedHashMap<>();
        datasets.computeIfAbsent(datasetId, id -> new ArrayList<>()).add(row);
        return row;
    }

    /** Thêm 1 dòng có rowtype: "insert" / "update" / "delete" (giống Dataset:U của Nexacro). */
    public Map<String, Object> row(String datasetId, String rowType) {
        Map<String, Object> row = row(datasetId);
        row.put("_rowType", rowType);
        return row;
    }
}
