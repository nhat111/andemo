package com.example.andemo.repository;

import com.example.andemo.entity.StorePin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorePinRepository extends JpaRepository<StorePin, String> {
}
