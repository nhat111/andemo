package com.example.andemo.disposal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, String> {

    /**
     * Khóa các dòng tồn kho (SELECT … FOR UPDATE) trong lúc trừ: 2 phiếu hủy cùng mặt hàng
     * xác nhận cùng lúc thì phiếu sau phải chờ, rồi kiểm tra lại số lượng khả dụng mới nhất.
     * Sắp xếp theo mã để các giao dịch luôn khóa cùng thứ tự, tránh deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.itemCode in :codes order by i.itemCode")
    List<InventoryItem> lockByItemCodes(@Param("codes") Collection<String> codes);
}
