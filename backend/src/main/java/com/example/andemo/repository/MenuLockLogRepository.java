package com.example.andemo.repository;

import com.example.andemo.entity.MenuLockLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuLockLogRepository extends JpaRepository<MenuLockLog, Long> {
    List<MenuLockLog> findTop50ByStoreIdOrderByLogIdDesc(String storeId);
}
