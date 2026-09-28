package com.example.andemo.repository;

import com.example.andemo.entity.PdaAlert;
import com.example.andemo.entity.PdaAlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PdaAlertRepository extends JpaRepository<PdaAlert, String> {

    List<PdaAlert> findByStatusAndExpiresAtAfter(PdaAlertStatus status, Instant now);

    List<PdaAlert> findAllByOrderByCreatedAtDesc();
}
