package com.example.andemo.disposal;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Dữ liệu mẫu cho demo 폐기 (chỉ tạo khi bảng tồn kho trống). Giá theo KRW.
 * Hôm qua đã 마감 (chốt sổ), hôm nay chưa.
 *
 * Hôm nay (등록): -0001 đủ tồn · -0002 đủ tồn (của user2) · -0003 THIẾU tồn khả dụng
 * Hôm qua: 1 phiếu 확정 (đã chốt sổ → không 확정취소 được) · 1 phiếu 취소
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
    static final String KIMBAP = "8801111222333";

    private final InventoryItemRepository inventoryRepository;
    private final DisposalRequestRepository disposalRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final StoreClosingRepository closingRepository;
    private final DocumentSequenceRepository sequenceRepository;
    private final BusinessCalendar calendar;

    @Override
    @Transactional
    public void run(String... args) {
        if (inventoryRepository.count() > 0) {
            return;
        }
        String store = DisposalService.STORE_CODE;
        InventoryItem water = save(new InventoryItem(WATER, "Nước suối Vĩnh Hảo 500ml", store, 120, 0, 450, 900));
        InventoryItem noodle = save(new InventoryItem(NOODLE, "Mì Hảo Hảo tôm chua cay", store, 85, 10, 600, 1100));
        InventoryItem milk = save(new InventoryItem(MILK, "Sữa TH True Milk 1L", store, 40, 0, 1900, 2980));
        InventoryItem cookie = save(new InventoryItem(COOKIE, "Bánh quy Cosy", store, 60, 0, 1200, 2000));
        InventoryItem coffee = save(new InventoryItem(COFFEE, "Cà phê G7 3in1 (hộp 18 gói)", store, 25, 5, 3500, 5500));
        InventoryItem sting = save(new InventoryItem(STING, "Nước tăng lực Sting dâu 330ml", store, 196, 0, 700, 1300));
        InventoryItem oil = save(new InventoryItem(OIL, "Dầu ăn Neptune 1L", store, 0, 0, 2800, 4200));
        // Hàng tươi (도시락 / 김밥) là nhóm bị hủy nhiều nhất ở cửa hàng tiện lợi Hàn Quốc
        InventoryItem kimbap = save(new InventoryItem(KIMBAP, "Kimbap cá ngừ (삼각김밥 참치마요)", store, 12, 0, 800, 1500));

        LocalDate today = calendar.today();
        LocalDate yesterday = today.minusDays(1);
        Instant now = Instant.now();

        DisposalRequest d1 = header(today, 1, "user", now.minus(1, ChronoUnit.HOURS), "Hàng tươi hết hạn ca sáng");
        d1.addItem(kimbap, 3, DisposalReason.EXPIRED);
        d1.addItem(milk, 2, DisposalReason.EXPIRED);
        d1.addItem(noodle, 1, DisposalReason.DAMAGED);
        disposalRepository.save(d1);

        DisposalRequest d2 = header(today, 2, "user2", now.minus(2, ChronoUnit.HOURS), "Vỡ khi lên kệ");
        d2.addItem(water, 3, DisposalReason.DAMAGED);
        disposalRepository.save(d2);

        // Cà phê: tồn 25, giữ chỗ 5 → khả dụng 20 < 30. Dầu ăn: tồn 0.
        DisposalRequest d3 = header(today, 3, "user", now.minus(3, ChronoUnit.HOURS), "Thu hồi theo thông báo NCC");
        d3.addItem(coffee, 30, DisposalReason.RECALL);
        d3.addItem(oil, 2, DisposalReason.RECALL);
        d3.addItem(cookie, 5, DisposalReason.QUALITY);
        disposalRepository.save(d3);

        DisposalRequest confirmed = header(yesterday, 1, "user", now.minus(1, ChronoUnit.DAYS), "Móp lon");
        DisposalItem stingLine = confirmed.addItem(sting, 4, DisposalReason.DAMAGED);
        confirmed.setStatus(DisposalStatus.CONFIRMED);
        confirmed.setConfirmedBy("admin");
        confirmed.setConfirmedAt(now.minus(20, ChronoUnit.HOURS));
        disposalRepository.save(confirmed);
        // 수불 khớp với phiếu đã xác nhận: Sting 200 → 196
        InventoryTransaction tx = new InventoryTransaction();
        tx.setStoreCode(store);
        tx.setBusinessDate(yesterday);
        tx.setItemCode(STING);
        tx.setTxType(InventoryTransaction.TYPE_DISPOSAL);
        tx.setQtyChange(-4);
        tx.setBeforeQty(200);
        tx.setAfterQty(196);
        tx.setCostAmount(-4 * stingLine.getCostPrice());
        tx.setRefNo(confirmed.getDisposalNo());
        tx.setRefLineNo(1);
        tx.setCreatedBy("admin");
        tx.setCreatedAt(confirmed.getConfirmedAt());
        transactionRepository.save(tx);

        DisposalRequest cancelled = header(yesterday, 2, "user2", now.minus(2, ChronoUnit.DAYS), null);
        cancelled.addItem(cookie, 1, DisposalReason.OTHER);
        cancelled.setStatus(DisposalStatus.CANCELLED);
        cancelled.setCancelledBy("user2");
        cancelled.setCancelledAt(now.minus(2, ChronoUnit.DAYS));
        cancelled.setCancelReason("Đăng ký nhầm");
        disposalRepository.save(cancelled);

        // Số thứ tự đã dùng, để phiếu đăng ký mới hôm nay tiếp tục từ 0004
        seq(today, 3);
        seq(yesterday, 2);

        StoreClosing closing = new StoreClosing();
        closing.setStoreCode(store);
        closing.setLastClosedDate(yesterday);
        closing.setClosedBy("admin");
        closing.setClosedAt(now.minus(6, ChronoUnit.HOURS));
        closingRepository.save(closing);
    }

    private InventoryItem save(InventoryItem item) {
        return inventoryRepository.save(item);
    }

    private static DisposalRequest header(LocalDate date, int seq, String user, Instant at, String remark) {
        DisposalRequest d = new DisposalRequest();
        d.setDisposalNo(String.format("%s-%s-%04d", DisposalService.STORE_CODE,
                date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE), seq));
        d.setStoreCode(DisposalService.STORE_CODE);
        d.setBusinessDate(date);
        d.setRegisteredBy(user);
        d.setRegisteredAt(at);
        d.setRemark(remark);
        return d;
    }

    private void seq(LocalDate date, int last) {
        DocumentSequence s = new DocumentSequence("DSP-" + DisposalService.STORE_CODE + "-"
                + date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
        s.setLastSeq(last);
        sequenceRepository.save(s);
    }
}
