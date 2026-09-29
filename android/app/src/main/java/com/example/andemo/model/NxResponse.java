package com.example.andemo.model;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Kết quả từ cổng /api/nx/**: ErrorCode / ErrorMsg + Parameters + các Dataset (mỗi dòng 1 object).
 * Tương đương các tham số của callback fn_callback(svcID, errorCode, errorMsg) + out-dataset.
 */
public class NxResponse {

    private static final Gson GSON = new Gson();

    private int errorCode;
    private String errorMsg;
    private Map<String, JsonElement> params;
    private Map<String, JsonElement> datasets;

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public boolean isSuccess() {
        return errorCode >= 0;
    }

    public String param(String name) {
        JsonElement v = params == null ? null : params.get(name);
        return v == null || v.isJsonNull() ? null : v.getAsString();
    }

    /**
     * Đổi 1 Dataset sang danh sách object Java, ví dụ dataset("ds_list", ProductDto.class).
     * Tên cột phải trùng tên field của class (giống khi dùng Retrofit + Gson bình thường).
     */
    public <T> List<T> dataset(String id, Class<T> type) {
        JsonElement rows = datasets == null ? null : datasets.get(id);
        if (rows == null || !rows.isJsonArray()) {
            return Collections.emptyList();
        }
        return GSON.fromJson(rows, TypeToken.getParameterized(List.class, type).getType());
    }

    /** Dataset dạng thô (để xem / debug). */
    public String datasetJson(String id) {
        JsonElement rows = datasets == null ? null : datasets.get(id);
        return rows == null ? "[]" : rows.toString();
    }

    /**
     * Lỗi nghiệp vụ (ErrorCode &lt; 0) backend trả HTTP 400 kèm body cùng định dạng:
     * Retrofit coi là lỗi nên phải tự đọc errorBody.
     */
    public static NxResponse fromError(Response<?> response) {
        NxResponse res = null;
        try (ResponseBody body = response.errorBody()) {
            if (body != null) {
                res = GSON.fromJson(body.string(), NxResponse.class);
            }
        } catch (IOException | RuntimeException ignored) {
            // body không phải JSON (ví dụ 401 / 502 từ proxy): dùng mã HTTP bên dưới
        }
        if (res == null) {
            res = new NxResponse();
            res.errorCode = -response.code();
            res.errorMsg = "HTTP " + response.code();
        }
        return res;
    }
}
