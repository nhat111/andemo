package com.example.andemo.disposal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, String> {

    /** Khóa dòng khi lấy số tiếp theo: 2 người đăng ký cùng lúc không bị trùng số phiếu */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocumentSequence s where s.sequenceKey = :key")
    Optional<DocumentSequence> lock(@Param("key") String key);
}
