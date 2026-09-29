package com.example.andemo.disposal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DisposalRequestRepository extends JpaRepository<DisposalRequest, String> {

    List<DisposalRequest> findAllByOrderByRequestedAtDesc();

    List<DisposalRequest> findByStatusOrderByRequestedAtDesc(DisposalStatus status);
}
