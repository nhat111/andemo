package com.example.andemo.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Mật khẩu 4 số (비밀번호) của cửa hàng dùng để mở khóa menu */
@Entity
@Table(name = "store_pin")
@Data
@NoArgsConstructor
public class StorePin {
    @Id
    private String storeId;

    @Column(nullable = false)
    private String pinHash;     // BCrypt, không lưu plain text

    private int failCount;      // số lần nhập sai liên tiếp

    private boolean otpRequired; // sai quá N lần → bắt buộc xác thực OTP

    private LocalDateTime updatedAt;
}
