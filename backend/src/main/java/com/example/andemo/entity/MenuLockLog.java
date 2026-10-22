package com.example.andemo.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Log lịch sử khóa / mở khóa / nhập sai PIN */
@Entity
@Table(name = "menu_lock_log")
@Data
@NoArgsConstructor
public class MenuLockLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    @Column(nullable = false)
    private String storeId;

    private String menuCode;    // null với action PIN_SET

    @Column(nullable = false)
    private String actionType;  // LOCK / UNLOCK / FAILED_TRY / PIN_SET

    private String actorId;
    private LocalDateTime createdAt;
    private String ipAddress;
}
