package com.example.andemo.nexacro;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chuyển NexacroData ↔ cấu trúc JSON (Map / List, Jackson tự ghi ra JSON).
 *
 * <pre>
 * {
 *   "errorCode": 0, "errorMsg": "SUCC",          ← tách từ Parameters ErrorCode / ErrorMsg
 *   "params":   { "savedCount": 2 },            ← các Parameter còn lại
 *   "datasets": {
 *     "ds_list": [ { "barcode": "890…", "stockQuantity": 120 }, … ]   ← mỗi dòng 1 object
 *   }
 * }
 * </pre>
 * Dòng có rowtype mang thêm "_rowType" ("insert" / "update" / "delete"), dòng "update"
 * mang thêm "_orgRow" (giá trị gốc) – giống Dataset:U của Nexacro.
 */
public final class NexacroJson {

    private NexacroJson() {
    }

    public static Map<String, Object> toJson(NexacroData data) {
        Map<String, Object> params = new LinkedHashMap<>(data.getParams());
        Object code = params.remove(NexacroData.ERROR_CODE);
        Object msg = params.remove(NexacroData.ERROR_MSG);

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("errorCode", code == null ? 0 : ((Number) code).intValue());
        json.put("errorMsg", msg == null ? "" : msg.toString());
        json.put("params", params);
        Map<String, Object> datasets = new LinkedHashMap<>();
        data.getDatasets().forEach((id, ds) -> datasets.put(id, ds.getRows()));
        json.put("datasets", datasets);
        return json;
    }

    /**
     * JSON từ app → NexacroData để gửi cho server X-API.
     * Cột và kiểu cột suy ra từ các dòng (số nguyên → int, số thập phân → bigdecimal, còn lại string).
     */
    @SuppressWarnings("unchecked")
    public static NexacroData fromJson(Map<String, Object> json) {
        NexacroData data = new NexacroData();
        Object params = json.get("params");
        if (params instanceof Map<?, ?> p) {
            data.getParams().putAll((Map<String, Object>) p);
        }
        Object datasets = json.get("datasets");
        if (datasets instanceof Map<?, ?> dsMap) {
            dsMap.forEach((id, rows) -> {
                NxDataset ds = new NxDataset(id.toString());
                List<Map<String, Object>> rowList = rows instanceof List<?> l
                        ? (List<Map<String, Object>>) l : new ArrayList<>();
                for (Map<String, Object> row : rowList) {
                    row.forEach((name, value) -> {
                        if (!name.startsWith("_")) {
                            String type = NexacroXml.typeOf(value);
                            ds.getColumns().merge(name, type, (old, t) -> "string".equals(old) ? t : old);
                        }
                    });
                    ds.getRows().add(new LinkedHashMap<>(row));
                }
                data.addDataset(ds);
            });
        }
        return data;
    }
}
