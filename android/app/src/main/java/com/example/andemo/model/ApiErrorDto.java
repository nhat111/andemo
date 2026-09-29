package com.example.andemo.model;

import com.google.gson.Gson;

import java.util.Collections;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Response;

/** Lỗi nghiệp vụ server trả về: {"code": "...", "message": "...", "details": [...]} */
public class ApiErrorDto {
    private String code;
    private String message;
    private List<String> details;

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public List<String> getDetails() { return details == null ? Collections.emptyList() : details; }

    /** Đọc errorBody của response lỗi; body không đúng định dạng thì trả lỗi chung theo mã HTTP. */
    public static ApiErrorDto from(Response<?> response) {
        ApiErrorDto error = null;
        try (ResponseBody body = response.errorBody()) {
            if (body != null) {
                error = new Gson().fromJson(body.string(), ApiErrorDto.class);
            }
        } catch (Exception ignored) {
            // không phải JSON
        }
        if (error == null || error.message == null) {
            error = new ApiErrorDto();
            error.code = "HTTP_" + response.code();
            error.message = response.code() == 403 ? "Bạn không có quyền thực hiện thao tác này"
                    : "Lỗi server: HTTP " + response.code();
        }
        return error;
    }
}
