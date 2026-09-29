package com.example.andemo.disposal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    List<InventoryTransaction> findByItemCodeOrderByIdDesc(String itemCode);

    List<InventoryTransaction> findByRefNoOrderByRefLineNo(String refNo);
}
