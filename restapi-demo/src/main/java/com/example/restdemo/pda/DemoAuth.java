package com.example.restdemo.pda;

import javax.servlet.http.HttpServletRequest;

/**
 * Lấy người dùng đang đăng nhập + mã thiết bị từ request.
 *
 * DEMO: đọc header X-Store-Cd / X-User-Id cho dễ thử bằng curl.
 * DỰ ÁN THẬT: thay currentUser(...) bằng cách dự án đang dùng, ví dụ:
 *   - Session:      (LoginUser) request.getSession(false).getAttribute("LOGIN_USER")
 *   - JWT + Spring Security: SecurityContextHolder.getContext().getAuthentication()
 * Đây là chỗ DUY NHẤT phụ thuộc cách đăng nhập của dự án.
 */
public final class DemoAuth {

    /** Header app PDA gửi trong MỌI request (thêm 1 chỗ ở lớp HTTP dùng chung của app) */
    public static final String HEADER_UNIQUE_ID = "X-Unique-Id";

    private DemoAuth() {
    }

    /** null = chưa đăng nhập */
    public static LoginUser currentUser(HttpServletRequest request) {
        String storeCd = request.getHeader("X-Store-Cd");
        String userId = request.getHeader("X-User-Id");
        if (isBlank(storeCd) || isBlank(userId)) {
            return null;
        }
        return new LoginUser(storeCd, userId);
    }

    /** null = request không đến từ PDA (ví dụ từ PC) */
    public static String uniqueId(HttpServletRequest request) {
        String id = request.getHeader(HEADER_UNIQUE_ID);
        return isBlank(id) ? null : id.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
