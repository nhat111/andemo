package com.example.andemo.plain;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * JSON → HashMap: mỗi object thành 1 HashMap&lt;String, String&gt; (giống 1 dòng Dataset của Nexacro),
 * mảng thành List các HashMap. Giá trị đều là chuỗi; object / mảng lồng nhau giữ dạng chuỗi JSON.
 * Adapter (PlainDisposalListAdapter...) đọc theo key: row.get("disposalNo").
 */
public final class JsonRows {

    private JsonRows() {
    }

    public static List<HashMap<String, String>> rows(String jsonArray) throws JSONException {
        JSONArray arr = new JSONArray(jsonArray);
        List<HashMap<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            rows.add(row(arr.getJSONObject(i)));
        }
        return rows;
    }

    public static HashMap<String, String> row(JSONObject o) {
        HashMap<String, String> row = new HashMap<>();
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            Object v = o.opt(k);
            row.put(k, v == null || v == JSONObject.NULL ? "" : v.toString());
        }
        return row;
    }

    /** "1234567" → "1,234,567원" (giá trị rỗng / không phải số → giữ nguyên) */
    public static String won(String number) {
        try {
            return String.format(java.util.Locale.US, "%,d원", Long.parseLong(number));
        } catch (NumberFormatException e) {
            return number;
        }
    }

    public static String statusLabel(String status) {
        if ("REGISTERED".equals(status)) return "Đã đăng ký (등록)";
        if ("CONFIRMED".equals(status)) return "Đã xác nhận (확정)";
        if ("CANCELLED".equals(status)) return "Đã hủy (취소)";
        return status;
    }
}
