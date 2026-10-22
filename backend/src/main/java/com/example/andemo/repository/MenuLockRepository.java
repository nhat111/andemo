package com.example.andemo.repository;

import com.example.andemo.entity.MenuLock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MenuLockRepository extends JpaRepository<MenuLock, Long> {
    List<MenuLock> findByStoreId(String storeId);

    Optional<MenuLock> findByStoreIdAndMenuCode(String storeId, String menuCode);
}
