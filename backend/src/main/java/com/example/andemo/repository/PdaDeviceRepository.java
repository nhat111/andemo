package com.example.andemo.repository;

import com.example.andemo.entity.PdaDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PdaDeviceRepository extends JpaRepository<PdaDevice, String> {

    List<PdaDevice> findAllByOrderByLastSeenAtDesc();

    List<PdaDevice> findByFcmTokenIsNotNull();

    List<PdaDevice> findByFcmToken(String fcmToken);
}
