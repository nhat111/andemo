package com.example.andemo.disposal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 마감 (chốt sổ): ngày kinh doanh gần nhất đã chốt của cửa hàng. Mọi chứng từ có 영업일자 ≤ ngày này
 * không được đăng ký / sửa / xác nhận / hủy xác nhận nữa (số liệu đã gửi 본부 / kế toán).
 */
@Entity
@Table(name = "store_closing")
@Data
@NoArgsConstructor
public class StoreClosing {

    @Id
    private String storeCode;

    private LocalDate lastClosedDate;

    private String closedBy;
    private Instant closedAt;

    public boolean isClosed(LocalDate businessDate) {
        return lastClosedDate != null && !businessDate.isAfter(lastClosedDate);
    }
}
