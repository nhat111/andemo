package com.example.andemo.disposal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 폐기 (hủy hàng) theo luồng bán lẻ Hàn Quốc: 등록 → (수정 / 취소) → 확정 → (확정취소).
 * Task 17 (tra cứu, chi tiết, xác nhận, kiểm tra trạng thái) + Task 18 (trừ tồn, đồng bộ, 수불, kiểm tra tồn khả dụng).
 *
 * Mọi thao tác ghi đều trong 1 transaction DB: thành công hết hoặc không đổi gì.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisposalService {

    /** Demo 1 cửa hàng. Hệ thống thật: lấy 점포코드 theo user đăng nhập. */
    public static final String STORE_CODE = "S001";
    private static final long MAX_QTY_PER_LINE = 9_999;
    /** R5: ghi chú (비고) tối đa 100 ký tự (= độ dài cột ở hệ thống cũ) */
    private static final int MAX_REMARK_LENGTH = 100;
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final DisposalRequestRepository disposalRepository;
    private final InventoryItemRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final StoreClosingRepository closingRepository;
    private final DocumentSequenceRepository sequenceRepository;
    private final BusinessCalendar calendar;

    // ======================= Task 17: tra cứu / chi tiết =======================

    @Transactional(readOnly = true)
    public List<DisposalDtos.Summary> list(DisposalStatus status, LocalDate from, LocalDate to) {
        // R1: từ ngày ≤ đến ngày
        if (from != null && to != null && from.isAfter(to)) {
            throw new DisposalException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "Từ ngày phải ≤ đến ngày (시작일이 종료일보다 늦습니다)");
        }
        return disposalRepository.search(status, from, to).stream()
                .map(d -> new DisposalDtos.Summary(d.getDisposalNo(), d.getStoreCode(), d.getBusinessDate(),
                        d.getStatus(), d.getStatus().getCode(), d.getStatus().getKoreanName(), d.getRemark(),
                        d.getRegisteredBy(), d.getRegisteredAt(), d.getItems().size(), d.getTotalQty(),
                        d.getTotalCostAmount(), d.getTotalSaleAmount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DisposalDtos.Detail detail(String disposalNo, String username, boolean admin) {
        return toDetail(find(disposalNo), username, admin);
    }

    // ======================= 등록 / 수정 / 취소 =======================

    /** 폐기등록: nhân viên quét hàng cần hủy trên PDA. Chưa trừ tồn. */
    @Transactional
    public DisposalDtos.Detail register(DisposalDtos.SaveRequest request, String username, boolean admin) {
        LocalDate businessDate = calendar.today();
        ensureNotClosed(businessDate);
        Map<String, InventoryItem> items = validateLines(request.items());

        DisposalRequest d = new DisposalRequest();
        d.setDisposalNo(nextDisposalNo(businessDate));
        d.setStoreCode(STORE_CODE);
        d.setBusinessDate(businessDate);
        d.setRemark(validRemark(request.remark()));
        d.setRegisteredBy(username);
        d.setRegisteredAt(Instant.now());
        for (DisposalDtos.LineInput line : request.items()) {
            d.addItem(items.get(line.itemCode().trim()), line.qty(), line.reasonCode());
        }
        disposalRepository.saveAndFlush(d);
        log.info("Disposal {} registered by {}: {} line(s)", d.getDisposalNo(), username, d.getItems().size());
        return toDetail(d, username, admin);
    }

    /** 폐기수정: chỉ phiếu 등록, người đăng ký hoặc quản lý, ngày chưa 마감. */
    @Transactional
    public DisposalDtos.Detail update(String disposalNo, DisposalDtos.SaveRequest request,
                                      String username, boolean admin) {
        DisposalRequest d = find(disposalNo);
        requireStatus(d, DisposalStatus.REGISTERED, "sửa");
        requireOwnerOrAdmin(d, username, admin);
        requireVersion(d, request.version());
        ensureNotClosed(d.getBusinessDate());
        Map<String, InventoryItem> items = validateLines(request.items());

        d.getItems().clear();
        disposalRepository.flush(); // xóa dòng cũ trước khi thêm dòng mới (line_no bắt đầu lại từ 1)
        for (DisposalDtos.LineInput line : request.items()) {
            d.addItem(items.get(line.itemCode().trim()), line.qty(), line.reasonCode());
        }
        d.setRemark(validRemark(request.remark()));
        d.setUpdatedBy(username);
        d.setUpdatedAt(Instant.now());
        disposalRepository.saveAndFlush(d);
        return toDetail(d, username, admin);
    }

    /** 폐기취소 (hủy phiếu chưa xác nhận): không đụng tồn kho. */
    @Transactional
    public DisposalDtos.Detail cancel(String disposalNo, DisposalDtos.ActionRequest request,
                                      String username, boolean admin) {
        DisposalRequest d = find(disposalNo);
        requireStatus(d, DisposalStatus.REGISTERED, "hủy");
        requireOwnerOrAdmin(d, username, admin);
        requireVersion(d, request == null ? null : request.version());
        ensureNotClosed(d.getBusinessDate());

        d.setStatus(DisposalStatus.CANCELLED);
        d.setCancelledBy(username);
        d.setCancelledAt(Instant.now());
        d.setCancelReason(requireReason(request));
        disposalRepository.saveAndFlush(d);
        return toDetail(d, username, admin);
    }

    // ======================= Task 17 + 18: 확정 =======================

    /** 폐기확정: quản lý xác nhận → trừ tồn + ghi 수불. Quyền quản lý kiểm tra ở controller. */
    @Transactional
    public DisposalDtos.Detail confirm(String disposalNo, DisposalDtos.ActionRequest request, String username) {
        DisposalRequest d = find(disposalNo);

        // Task 17 – Disposal Status Validation
        requireStatus(d, DisposalStatus.REGISTERED, "xác nhận");
        requireToday(d);
        requireVersion(d, request == null ? null : request.version());
        ensureNotClosed(d.getBusinessDate());

        Map<String, Long> required = requiredQtyByItem(d);
        // Task 18 – Inventory Quantity Synchronization: khóa dòng tồn tới hết transaction
        Map<String, InventoryItem> inventory = lockInventory(required.keySet());
        // Task 18 – Validation of Available Quantity: kiểm tra hết rồi mới trừ
        List<String> problems = shortages(required, inventory);
        if (!problems.isEmpty()) {
            throw new DisposalException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_QTY",
                    "Không đủ tồn khả dụng để hủy (가용재고 부족)", problems);
        }

        // Task 18 – Inventory Deduction Processing + Transaction History Update
        Instant now = Instant.now();
        for (DisposalItem line : d.getItems()) {
            moveStock(d, line, inventory.get(line.getItemCode()), -line.getQty(),
                    InventoryTransaction.TYPE_DISPOSAL, username, now);
        }
        d.setStatus(DisposalStatus.CONFIRMED);
        d.setConfirmedBy(username);
        d.setConfirmedAt(now);
        // Flush ngay: người khác vừa xử lý cùng phiếu (version đổi) → lỗi ở đây, rollback cả phần trừ tồn
        disposalRepository.saveAndFlush(d);

        log.info("Disposal {} confirmed by {}: cost {} KRW", disposalNo, username, d.getTotalCostAmount());
        return toDetail(d, username, true);
    }

    /**
     * 폐기확정취소: đảo lại phiếu đã xác nhận trong ngày chưa 마감 (cộng lại tồn, ghi 수불 ngược dấu),
     * phiếu quay về 등록 để sửa / hủy / xác nhận lại.
     */
    @Transactional
    public DisposalDtos.Detail cancelConfirm(String disposalNo, DisposalDtos.ActionRequest request, String username) {
        DisposalRequest d = find(disposalNo);
        requireStatus(d, DisposalStatus.CONFIRMED, "hủy xác nhận");
        requireVersion(d, request == null ? null : request.version());
        ensureNotClosed(d.getBusinessDate());
        String reason = requireReason(request);

        Map<String, InventoryItem> inventory = lockInventory(requiredQtyByItem(d).keySet());
        Instant now = Instant.now();
        for (DisposalItem line : d.getItems()) {
            InventoryItem item = inventory.get(line.getItemCode());
            if (item == null) {
                throw new DisposalException(HttpStatus.UNPROCESSABLE_ENTITY, "ITEM_NOT_FOUND",
                        "Mặt hàng " + line.getItemCode() + " không còn trong tồn kho, không đảo được");
            }
            moveStock(d, line, item, line.getQty(), InventoryTransaction.TYPE_DISPOSAL_CANCEL, username, now);
        }
        d.setStatus(DisposalStatus.REGISTERED);
        d.setConfirmedBy(null);
        d.setConfirmedAt(null);
        d.setConfirmCancelledBy(username);
        d.setConfirmCancelledAt(now);
        d.setConfirmCancelReason(reason);
        disposalRepository.saveAndFlush(d);

        log.info("Disposal {} confirmation cancelled by {}: {}", disposalNo, username, reason);
        return toDetail(d, username, true);
    }

    // ======================= tra cứu phụ trợ / 마감 =======================

    @Transactional(readOnly = true)
    public DisposalDtos.ItemInfo item(String itemCode) {
        InventoryItem i = inventoryRepository.findById(itemCode.trim()).orElseThrow(() ->
                new DisposalException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND",
                        "Không có mặt hàng " + itemCode + " trong tồn kho cửa hàng"));
        return new DisposalDtos.ItemInfo(i.getItemCode(), i.getItemName(), i.getOnHandQty(), i.getAvailableQty(),
                i.getCostPrice(), i.getSalePrice());
    }

    @Transactional(readOnly = true)
    public List<InventoryTransaction> history(String itemCode) {
        return transactionRepository.findByItemCodeOrderByIdDesc(itemCode);
    }

    public List<DisposalDtos.ReasonInfo> reasons() {
        return Arrays.stream(DisposalReason.values())
                .map(r -> new DisposalDtos.ReasonInfo(r, r.getKoreanName(), r.getVietnameseName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DisposalDtos.ClosingInfo closingInfo() {
        LocalDate last = closingRepository.findById(STORE_CODE).map(StoreClosing::getLastClosedDate).orElse(null);
        return new DisposalDtos.ClosingInfo(STORE_CODE, calendar.today(), last);
    }

    /** 일마감 (demo): chốt sổ tới ngày closeDate. Không cho chốt ngày tương lai hay lùi ngày đã chốt. */
    @Transactional
    public DisposalDtos.ClosingInfo close(LocalDate closeDate, String username) {
        if (closeDate == null || closeDate.isAfter(calendar.today())) {
            throw new DisposalException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "Ngày chốt sổ phải ≤ ngày kinh doanh hiện tại " + calendar.today());
        }
        StoreClosing closing = closingRepository.findById(STORE_CODE).orElseGet(() -> {
            StoreClosing c = new StoreClosing();
            c.setStoreCode(STORE_CODE);
            return c;
        });
        if (closing.getLastClosedDate() != null && closeDate.isBefore(closing.getLastClosedDate())) {
            throw new DisposalException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "Đã chốt sổ tới " + closing.getLastClosedDate() + ", không lùi được");
        }
        closing.setLastClosedDate(closeDate);
        closing.setClosedBy(username);
        closing.setClosedAt(Instant.now());
        closingRepository.save(closing);
        return closingInfo();
    }

    // ======================= helpers =======================

    private DisposalRequest find(String disposalNo) {
        return disposalRepository.findById(disposalNo).orElseThrow(() ->
                new DisposalException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy phiếu hủy " + disposalNo));
    }

    private static void requireStatus(DisposalRequest d, DisposalStatus expected, String action) {
        if (d.getStatus() != expected) {
            throw new DisposalException(HttpStatus.CONFLICT, "INVALID_STATUS",
                    "Phiếu đang ở trạng thái " + d.getStatus().getKoreanName() + " (" + d.getStatus().getCode()
                            + "), không " + action + " được. Chỉ phiếu " + expected.getKoreanName()
                            + " (" + expected.getCode() + ") mới " + action + " được.");
        }
    }

    private static final String NOT_TODAY_MESSAGE =
            "Chỉ xác nhận phiếu của 영업일자 hôm nay (당일 전표만 확정 가능)";

    /** R9: chỉ xác nhận phiếu của ngày kinh doanh hiện tại (hệ thống cũ kiểm tra ở client, nay đưa về server) */
    private void requireToday(DisposalRequest d) {
        if (!d.getBusinessDate().equals(calendar.today())) {
            throw new DisposalException(HttpStatus.CONFLICT, "NOT_TODAY", NOT_TODAY_MESSAGE);
        }
    }

    /** R5: trim + giới hạn độ dài ghi chú */
    private static String validRemark(String remark) {
        String r = trim(remark);
        if (r != null && r.length() > MAX_REMARK_LENGTH) {
            throw new DisposalException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "Ghi chú tối đa " + MAX_REMARK_LENGTH + " ký tự (비고는 100자 이내)");
        }
        return r;
    }

    private static void requireVersion(DisposalRequest d, Long expected) {
        if (expected != null && expected != d.getVersion()) {
            throw new DisposalException(HttpStatus.CONFLICT, "STALE_DATA",
                    "Phiếu đã được người khác thay đổi. Vui lòng tải lại.");
        }
    }

    private static void requireOwnerOrAdmin(DisposalRequest d, String username, boolean admin) {
        if (!admin && !Objects.equals(d.getRegisteredBy(), username)) {
            throw new DisposalException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Chỉ người đăng ký hoặc quản lý mới được thao tác phiếu này");
        }
    }

    private static String requireReason(DisposalDtos.ActionRequest request) {
        String reason = request == null ? null : trim(request.reason());
        if (reason == null) {
            throw new DisposalException(HttpStatus.BAD_REQUEST, "VALIDATION", "Phải nhập lý do hủy (취소사유)");
        }
        return reason;
    }

    private void ensureNotClosed(LocalDate businessDate) {
        if (isClosed(businessDate)) {
            throw new DisposalException(HttpStatus.CONFLICT, "CLOSED_PERIOD",
                    "영업일자 " + businessDate + " đã chốt sổ (마감), không thay đổi được");
        }
    }

    private boolean isClosed(LocalDate businessDate) {
        return closingRepository.findById(STORE_CODE).map(c -> c.isClosed(businessDate)).orElse(false);
    }

    /** Kiểm tra dữ liệu nhập; trả về mặt hàng theo mã. Lỗi → 400 kèm danh sách mọi dòng sai. */
    private Map<String, InventoryItem> validateLines(List<DisposalDtos.LineInput> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new DisposalException(HttpStatus.BAD_REQUEST, "VALIDATION", "Phiếu phải có ít nhất 1 mặt hàng");
        }
        Set<String> codes = lines.stream().map(DisposalDtos.LineInput::itemCode)
                .filter(Objects::nonNull).map(String::trim).collect(Collectors.toSet());
        Map<String, InventoryItem> items = inventoryRepository.findAllById(codes).stream()
                .collect(Collectors.toMap(InventoryItem::getItemCode, Function.identity()));

        List<String> errors = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            DisposalDtos.LineInput line = lines.get(i);
            String prefix = "Dòng " + (i + 1) + ": ";
            String code = line.itemCode() == null ? "" : line.itemCode().trim();
            if (code.isEmpty()) {
                errors.add(prefix + "thiếu mã hàng");
                continue;
            }
            Integer first = seen.putIfAbsent(code, i + 1);
            if (first != null) {
                errors.add(prefix + "mặt hàng " + code + " trùng với dòng " + first + " (gộp số lượng vào 1 dòng)");
            }
            if (!items.containsKey(code)) {
                errors.add(prefix + "không có mặt hàng " + code + " trong tồn kho cửa hàng");
            }
            if (line.qty() == null || line.qty() <= 0) {
                errors.add(prefix + "số lượng phải > 0");
            } else if (line.qty() > MAX_QTY_PER_LINE) {
                errors.add(prefix + "số lượng tối đa " + MAX_QTY_PER_LINE);
            }
            if (line.reasonCode() == null) {
                errors.add(prefix + "chưa chọn lý do hủy (폐기사유)");
            }
        }
        if (!errors.isEmpty()) {
            throw new DisposalException(HttpStatus.BAD_REQUEST, "VALIDATION", "Dữ liệu phiếu hủy không hợp lệ", errors);
        }
        return items;
    }

    /** 전표번호 S001-20260929-0001: khóa dòng số thứ tự để không trùng khi nhiều người đăng ký cùng lúc */
    private String nextDisposalNo(LocalDate businessDate) {
        String day = businessDate.format(YYYYMMDD);
        String key = "DSP-" + STORE_CODE + "-" + day;
        DocumentSequence seq = sequenceRepository.lock(key)
                .orElseGet(() -> sequenceRepository.saveAndFlush(new DocumentSequence(key)));
        seq.setLastSeq(seq.getLastSeq() + 1);
        return String.format("%s-%s-%04d", STORE_CODE, day, seq.getLastSeq());
    }

    private Map<String, InventoryItem> lockInventory(Collection<String> codes) {
        return inventoryRepository.lockByItemCodes(codes).stream()
                .collect(Collectors.toMap(InventoryItem::getItemCode, Function.identity()));
    }

    private void moveStock(DisposalRequest d, DisposalItem line, InventoryItem item, long change,
                           String type, String username, Instant now) {
        long before = item.getOnHandQty();
        item.setOnHandQty(before + change);

        InventoryTransaction tx = new InventoryTransaction();
        tx.setStoreCode(d.getStoreCode());
        tx.setBusinessDate(d.getBusinessDate());
        tx.setItemCode(line.getItemCode());
        tx.setTxType(type);
        tx.setQtyChange(change);
        tx.setBeforeQty(before);
        tx.setAfterQty(item.getOnHandQty());
        tx.setCostAmount(change * line.getCostPrice());
        tx.setRefNo(d.getDisposalNo());
        tx.setRefLineNo(line.getLineNo());
        tx.setCreatedBy(username);
        tx.setCreatedAt(now);
        transactionRepository.save(tx);
    }

    /** Cùng mặt hàng nhiều dòng (dữ liệu cũ) thì cộng dồn; giữ thứ tự dòng để thông báo lỗi dễ đọc */
    private static Map<String, Long> requiredQtyByItem(DisposalRequest d) {
        Map<String, Long> required = new LinkedHashMap<>();
        for (DisposalItem line : d.getItems()) {
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
            } else if (item.getAvailableQty() < qty) {
                problems.add(code + " (" + item.getItemName() + "): cần hủy " + qty
                        + ", khả dụng " + item.getAvailableQty()
                        + " (tồn " + item.getOnHandQty() + ", đã giữ chỗ " + item.getAllocatedQty() + ")");
            }
        });
        return problems;
    }

    private DisposalDtos.Detail toDetail(DisposalRequest d, String username, boolean admin) {
        Map<String, Long> required = requiredQtyByItem(d);
        Map<String, InventoryItem> inventory = inventoryRepository.findAllById(required.keySet()).stream()
                .collect(Collectors.toMap(InventoryItem::getItemCode, Function.identity()));

        boolean closed = isClosed(d.getBusinessDate());
        boolean registered = d.getStatus() == DisposalStatus.REGISTERED;
        boolean ownerOrAdmin = admin || Objects.equals(d.getRegisteredBy(), username);

        List<String> issues = new ArrayList<>();
        if (registered) {
            if (closed) {
                issues.add("영업일자 " + d.getBusinessDate() + " đã chốt sổ (마감)");
            } else if (!d.getBusinessDate().equals(calendar.today())) {
                issues.add(NOT_TODAY_MESSAGE);
            }
            issues.addAll(shortages(required, inventory));
        }
        DisposalDtos.Actions actions = new DisposalDtos.Actions(
                registered && !closed && ownerOrAdmin,
                registered && !closed && ownerOrAdmin,
                registered && !closed && admin,
                d.getStatus() == DisposalStatus.CONFIRMED && !closed && admin);

        List<DisposalDtos.Line> lines = d.getItems().stream().map(line -> {
            InventoryItem item = inventory.get(line.getItemCode());
            boolean sufficient = item != null && item.getAvailableQty() >= required.get(line.getItemCode());
            return new DisposalDtos.Line(line.getLineNo(), line.getItemCode(), line.getItemName(), line.getQty(),
                    line.getReasonCode(), line.getReasonCode() == null ? null : line.getReasonCode().getKoreanName(),
                    line.getCostPrice(), line.getSalePrice(), line.getCostAmount(), line.getSaleAmount(),
                    item == null ? null : item.getOnHandQty(), item == null ? null : item.getAvailableQty(),
                    sufficient);
        }).toList();

        return new DisposalDtos.Detail(d.getDisposalNo(), d.getStoreCode(), d.getBusinessDate(),
                d.getStatus(), d.getStatus().getCode(), d.getStatus().getKoreanName(), d.getRemark(),
                d.getRegisteredBy(), d.getRegisteredAt(), d.getUpdatedBy(), d.getUpdatedAt(),
                d.getConfirmedBy(), d.getConfirmedAt(), d.getCancelledBy(), d.getCancelledAt(), d.getCancelReason(),
                d.getConfirmCancelledBy(), d.getConfirmCancelledAt(), d.getConfirmCancelReason(),
                d.getVersion(), d.getTotalQty(), d.getTotalCostAmount(), d.getTotalSaleAmount(),
                closed, actions, issues, lines);
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
