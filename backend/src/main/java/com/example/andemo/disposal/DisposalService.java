package com.example.andemo.disposal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Task 17 (xác nhận phiếu hủy) + Task 18 (cập nhật tồn kho).
 * Xác nhận và trừ tồn nằm trong CÙNG 1 transaction DB: hoặc cả 2 thành công, hoặc không có gì thay đổi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisposalService {

    private final DisposalRequestRepository disposalRepository;
    private final InventoryItemRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;

    // ---------------- Task 17: Disposal Inquiry Screen ----------------

    @Transactional(readOnly = true)
    public List<DisposalDtos.Summary> list(DisposalStatus status) {
        List<DisposalRequest> found = status == null
                ? disposalRepository.findAllByOrderByRequestedAtDesc()
                : disposalRepository.findByStatusOrderByRequestedAtDesc(status);
        return found.stream()
                .map(d -> new DisposalDtos.Summary(d.getDisposalNo(), d.getWarehouseCode(), d.getStatus(),
                        d.getReason(), d.getRequestedBy(), d.getRequestedAt(), d.getItems().size(),
                        d.getItems().stream().mapToLong(DisposalItem::getQty).sum()))
                .toList();
    }

    // ---------------- Task 17: Disposal Detail View ----------------

    @Transactional(readOnly = true)
    public DisposalDtos.Detail detail(String disposalNo) {
        return toDetail(find(disposalNo));
    }

    // ---------------- Task 17: Confirm Button + Task 18: Inventory Update ----------------

    /**
     * @param expectedVersion version app đang hiển thị (null = không kiểm tra)
     */
    @Transactional
    public DisposalDtos.Detail confirm(String disposalNo, Long expectedVersion, String username) {
        DisposalRequest disposal = find(disposalNo);

        // Task 17 – Disposal Status Validation
        validateConfirmable(disposal);
        if (expectedVersion != null && expectedVersion != disposal.getVersion()) {
            throw new DisposalException(HttpStatus.CONFLICT, "STALE_DATA",
                    "Phiếu đã được người khác thay đổi. Vui lòng tải lại trước khi xác nhận.");
        }

        // Cùng 1 mặt hàng có thể nằm ở nhiều dòng: cộng dồn trước khi so với tồn khả dụng
        Map<String, Long> required = requiredQtyByItem(disposal);

        // Task 18 – Inventory Quantity Synchronization: khóa dòng tồn kho tới hết transaction
        Map<String, InventoryItem> inventory = inventoryRepository.lockByItemCodes(required.keySet()).stream()
                .collect(Collectors.toMap(InventoryItem::getItemCode, Function.identity()));

        // Task 18 – Validation of Available Quantity: kiểm tra HẾT rồi mới trừ, báo đủ mọi dòng lỗi 1 lần
        List<String> problems = shortages(required, inventory);
        if (!problems.isEmpty()) {
            throw new DisposalException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_QTY",
                    "Không đủ số lượng khả dụng để hủy", problems);
        }

        // Task 18 – Inventory Deduction Processing + Transaction History Update
        Instant now = Instant.now();
        for (DisposalItem line : disposal.getItems()) {
            InventoryItem item = inventory.get(line.getItemCode());
            long before = item.getOnHandQty();
            item.setOnHandQty(before - line.getQty());

            InventoryTransaction tx = new InventoryTransaction();
            tx.setItemCode(line.getItemCode());
            tx.setTxType("DISPOSAL");
            tx.setQtyChange(-line.getQty());
            tx.setBeforeQty(before);
            tx.setAfterQty(item.getOnHandQty());
            tx.setRefNo(disposal.getDisposalNo());
            tx.setRefLineNo(line.getLineNo());
            tx.setCreatedBy(username);
            tx.setCreatedAt(now);
            transactionRepository.save(tx);
        }

        disposal.setStatus(DisposalStatus.CONFIRMED);
        disposal.setConfirmedBy(username);
        disposal.setConfirmedAt(now);
        // Flush ngay: nếu người khác vừa xác nhận cùng phiếu (version đổi), lỗi khóa lạc quan xảy ra ở đây
        // và cả transaction (kể cả phần trừ tồn) bị rollback
        disposalRepository.saveAndFlush(disposal);

        log.info("Disposal {} confirmed by {}: {} line(s), {} item(s) deducted",
                disposalNo, username, disposal.getItems().size(), required.size());
        return toDetail(disposal);
    }

    @Transactional(readOnly = true)
    public List<InventoryTransaction> history(String itemCode) {
        return transactionRepository.findByItemCodeOrderByIdDesc(itemCode);
    }

    @Transactional(readOnly = true)
    public InventoryItem inventory(String itemCode) {
        return inventoryRepository.findById(itemCode).orElseThrow(() ->
                new DisposalException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Không có mặt hàng " + itemCode));
    }

    // ---------------- helpers ----------------

    private DisposalRequest find(String disposalNo) {
        return disposalRepository.findById(disposalNo).orElseThrow(() ->
                new DisposalException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy phiếu hủy " + disposalNo));
    }

    private static void validateConfirmable(DisposalRequest disposal) {
        if (disposal.getStatus() != DisposalStatus.REQUESTED) {
            throw new DisposalException(HttpStatus.CONFLICT, "INVALID_STATUS", statusMessage(disposal.getStatus()));
        }
        if (disposal.getItems().isEmpty()) {
            throw new DisposalException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_ITEMS", "Phiếu hủy không có mặt hàng nào");
        }
    }

    private static String statusMessage(DisposalStatus status) {
        return switch (status) {
            case CONFIRMED -> "Phiếu đã được xác nhận trước đó";
            case CANCELLED -> "Phiếu đã bị hủy, không xác nhận được";
            case REQUESTED -> "";
        };
    }

    private static Map<String, Long> requiredQtyByItem(DisposalRequest disposal) {
        // Giữ thứ tự dòng trên phiếu để thông báo lỗi dễ đọc (khóa DB vẫn theo thứ tự mã, xem repository)
        Map<String, Long> required = new LinkedHashMap<>();
        for (DisposalItem line : disposal.getItems()) {
            required.merge(line.getItemCode(), line.getQty(), Long::sum);
        }
        return required;
    }

    private static List<String> shortages(Map<String, Long> required, Map<String, InventoryItem> inventory) {
        List<String> problems = new ArrayList<>();
        required.forEach((code, qty) -> {
            InventoryItem item = inventory.get(code);
            if (item == null) {
                problems.add(code + ": không có trong tồn kho");
            } else if (qty <= 0) {
                problems.add(code + ": số lượng hủy phải > 0");
            } else if (item.getAvailableQty() < qty) {
                problems.add(code + " (" + item.getItemName() + "): cần hủy " + qty
                        + ", khả dụng " + item.getAvailableQty()
                        + " (tồn " + item.getOnHandQty() + ", đã giữ chỗ " + item.getAllocatedQty() + ")");
            }
        });
        return problems;
    }

    private DisposalDtos.Detail toDetail(DisposalRequest d) {
        Map<String, Long> required = requiredQtyByItem(d);
        Map<String, InventoryItem> inventory = inventoryRepository.findAllById(required.keySet()).stream()
                .collect(Collectors.toMap(InventoryItem::getItemCode, Function.identity()));

        List<String> issues = new ArrayList<>();
        if (d.getStatus() != DisposalStatus.REQUESTED) {
            issues.add(statusMessage(d.getStatus()));
        } else {
            issues.addAll(shortages(required, inventory));
        }

        List<DisposalDtos.Line> lines = d.getItems().stream().map(line -> {
            InventoryItem item = inventory.get(line.getItemCode());
            boolean sufficient = item != null && item.getAvailableQty() >= required.get(line.getItemCode());
            return new DisposalDtos.Line(line.getLineNo(), line.getItemCode(),
                    item == null ? null : item.getItemName(), line.getQty(), line.getReasonCode(),
                    item == null ? null : item.getOnHandQty(), item == null ? null : item.getAvailableQty(),
                    sufficient);
        }).toList();

        return new DisposalDtos.Detail(d.getDisposalNo(), d.getWarehouseCode(), d.getStatus(), d.getReason(),
                d.getRequestedBy(), d.getRequestedAt(), d.getConfirmedBy(), d.getConfirmedAt(), d.getVersion(),
                issues.isEmpty(), issues, lines);
    }
}
