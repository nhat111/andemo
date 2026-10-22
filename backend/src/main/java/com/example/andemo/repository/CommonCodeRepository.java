package com.example.andemo.repository;

import com.example.andemo.entity.CommonCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommonCodeRepository extends JpaRepository<CommonCode, Long> {
    List<CommonCode> findByGroupCodeAndUseYnTrueOrderBySortOrder(String groupCode);

    Optional<CommonCode> findByGroupCodeAndCodeAndUseYnTrue(String groupCode, String code);

    long countByGroupCode(String groupCode);
}
