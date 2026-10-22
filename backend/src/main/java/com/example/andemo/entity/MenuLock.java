package com.example.andemo.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Trạng thái khóa của 1 menu tại 1 cửa hàng */
@Entity
@Table(name = "menu_lock",
        uniqueConstraints = @UniqueConstraint(columnNames = {"storeId", "menuCode"}))
@Data
@NoArgsConstructor
public class MenuLock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String storeId;

    @Column(nullable = false)
    private String menuCode;

    private boolean locked;

    private String updatedBy;
    private LocalDateTime updatedAt;
}
