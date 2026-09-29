package com.example.andemo.nexacro;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1 Dataset của Nexacro: danh sách cột (tên → kiểu) + các dòng.
 *
 * Mỗi dòng là Map tên cột → giá trị. Trạng thái dòng (rowtype) nằm trong key đặc biệt
 * {@link #ROW_TYPE} ("insert" / "update" / "delete"; không có = dòng bình thường),
 * giá trị gốc của dòng "update" nằm trong {@link #ORG_ROW}.
 */
public class NxDataset {

    public static final String ROW_TYPE = "_rowType";
    public static final String ORG_ROW = "_orgRow";

    private final String id;
    /** Tên cột → kiểu Nexacro (string, int, bigdecimal, date, datetime…), giữ thứ tự khai báo */
    private final Map<String, String> columns = new LinkedHashMap<>();
    private final List<Map<String, Object>> rows = new ArrayList<>();

    public NxDataset(String id) {
        this.id = id;
    }

    public NxDataset addColumn(String name, String type) {
        columns.put(name, type);
        return this;
    }

    public Map<String, Object> addRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        rows.add(row);
        return row;
    }

    public String getId() {
        return id;
    }

    public Map<String, String> getColumns() {
        return columns;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public String getString(int row, String column) {
        Object v = rows.get(row).get(column);
        return v == null ? null : v.toString();
    }

    public static String rowType(Map<String, Object> row) {
        Object t = row.get(ROW_TYPE);
        return t == null ? "normal" : t.toString();
    }
}
