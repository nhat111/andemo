package com.example.andemo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bảng COMMON_CODE dùng chung.
 * Group LOCK_MENU = danh sách menu có thể khóa → thêm menu mới chỉ cần thêm row, không sửa code.
 */
@Entity
@Table(name = "common_code",
        uniqueConstraints = @UniqueConstraint(columnNames = {"groupCode", "code"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommonCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String groupCode;   // vd: LOCK_MENU

    @Column(nullable = false)
    private String code;        // vd: ORDER, RETURN, COST

    @Column(nullable = false)
    private String codeName;    // tên hiển thị

    private String attr1;       // LOCK_MENU: BLOCK (chặn truy cập) / MASK (ẩn dữ liệu nhạy cảm)

    private int sortOrder;

    private boolean useYn = true;
}
