package com.example.andemo.disposal;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Dữ liệu mẫu cho demo phiếu hủy (chỉ tạo khi bảng tồn kho còn trống).
 * Mã hàng trùng barcode sản phẩm của ProductCatalog để tra cứu chéo được.
 *
 * - DSP-…-001, DSP-…-002: chờ xác nhận, đủ tồn → xác nhận được
 * - DSP-…-003: chờ xác nhận, THIẾU tồn khả dụng → báo lỗi, không trừ gì
 * - DSP-…-004: đã xác nhận; DSP-…-005: đã hủy → không xác nhận lại được
 */
@Component
@RequiredArgsConstructor
public class DisposalDataInitializer implements CommandLineRunner {

    static final String MILK = "8851993123456";
    static final String NOODLE = "8934567890123";
    static final String WATER = "8901234567890";
    static final String COOKIE = "8801234567890";
    static final String COFFEE = "8936036020151";
    static final String STING = "8934673573137";
    static final String OIL = "8935049510864";

    private final InventoryItemRepository inventoryRepository;
    private final DisposalRequestRepository disposalRepository;
    private final InventoryTransactionRepository transactionRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (inventoryRepository.count() > 0) {
            return;
        }
        String wh = "WH01";
        inventoryRepository.save(new InventoryItem(WATER, "Nước suối Vĩnh Hảo 500ml", wh, 120, 0));
        inventoryRepository.save(new InventoryItem(NOODLE, "Mì Hảo Hảo tôm chua cay", wh, 85, 10));
        inventoryRepository.save(new InventoryItem(MILK, "Sữa TH True Milk 1L", wh, 40, 0));
        inventoryRepository.save(new InventoryItem(COOKIE, "Bánh quy Cosy", wh, 60, 0));
        inventoryRepository.save(new InventoryItem(COFFEE, "Cà phê G7 3in1 (hộp 18 gói)", wh, 25, 5));
        inventoryRepository.save(new InventoryItem(STING, "Nước tăng lực Sting dâu 330ml", wh, 196, 0));
        inventoryRepository.save(new InventoryItem(OIL, "Dầu ăn Neptune 1L", wh, 0, 0));

        Instant now = Instant.now();
        disposalRepository.save(new DisposalRequest("DSP-20260929-001", wh, "Hàng hết hạn sử dụng", "user",
                now.minus(1, ChronoUnit.HOURS))
                .addItem(MILK, 5, "EXPIRED")
                .addItem(NOODLE, 10, "DAMAGED"));
        disposalRepository.save(new DisposalRequest("DSP-20260929-002", wh, "Vỡ trong lúc vận chuyển", "user2",
                now.minus(2, ChronoUnit.HOURS))
                .addItem(WATER, 3, "DAMAGED"));
        // Cà phê: tồn 25, giữ chỗ 5 → khả dụng 20 < 30. Dầu ăn: tồn 0.
        disposalRepository.save(new DisposalRequest("DSP-20260929-003", wh, "Thu hồi theo yêu cầu NCC", "user",
                now.minus(3, ChronoUnit.HOURS))
                .addItem(COFFEE, 30, "RECALL")
                .addItem(OIL, 2, "RECALL")
                .addItem(COOKIE, 5, "EXPIRED"));

        DisposalRequest confirmed = new DisposalRequest("DSP-20260928-001", wh, "Móp lon", "user",
                now.minus(1, ChronoUnit.DAYS)).addItem(STING, 4, "DAMAGED");
        confirmed.setStatus(DisposalStatus.CONFIRMED);
        confirmed.setConfirmedBy("admin");
        confirmed.setConfirmedAt(now.minus(20, ChronoUnit.HOURS));
        disposalRepository.save(confirmed);
        // Lịch sử khớp với phiếu đã xác nhận: Sting 200 → 196
        InventoryTransaction tx = new InventoryTransaction();
        tx.setItemCode(STING);
        tx.setTxType("DISPOSAL");
        tx.setQtyChange(-4);
        tx.setBeforeQty(200);
        tx.setAfterQty(196);
        tx.setRefNo(confirmed.getDisposalNo());
        tx.setRefLineNo(1);
        tx.setCreatedBy("admin");
        tx.setCreatedAt(confirmed.getConfirmedAt());
        transactionRepository.save(tx);

        DisposalRequest cancelled = new DisposalRequest("DSP-20260927-001", wh, "Nhập nhầm", "user2",
                now.minus(2, ChronoUnit.DAYS)).addItem(COOKIE, 1, "OTHER");
        cancelled.setStatus(DisposalStatus.CANCELLED);
        disposalRepository.save(cancelled);
    }
}
