package com.example.andemo.disposal;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * API 폐기 (hủy hàng) + tồn kho. Xem docs/task-17-18-disposal/README.md.
 *
 * Quyền: 점원 (USER) đăng ký / sửa / hủy phiếu của mình; 점장 (ADMIN) xác nhận, hủy xác nhận, chốt sổ.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DisposalController {

    private final DisposalService service;

    // ---------------- 폐기조회 / 상세 ----------------

    /** status: REGISTERED / CONFIRMED / CANCELLED hoặc mã 10 / 20 / 90; trống hoặc ALL = tất cả. from/to: 영업일자 yyyy-MM-dd */
    @GetMapping("/disposals")
    public List<DisposalDtos.Summary> list(@RequestParam(required = false) String status,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(parseStatus(status), from, to);
    }

    @GetMapping("/disposals/{disposalNo}")
    public DisposalDtos.Detail detail(@PathVariable String disposalNo, Authentication auth) {
        return service.detail(disposalNo, auth.getName(), isAdmin(auth));
    }

    // ---------------- 등록 / 수정 / 취소 ----------------

    @PostMapping("/disposals")
    public ResponseEntity<DisposalDtos.Detail> register(@RequestBody DisposalDtos.SaveRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(request, auth.getName(), isAdmin(auth)));
    }

    @PutMapping("/disposals/{disposalNo}")
    public DisposalDtos.Detail update(@PathVariable String disposalNo, @RequestBody DisposalDtos.SaveRequest request,
                                      Authentication auth) {
        return service.update(disposalNo, request, auth.getName(), isAdmin(auth));
    }

    @PostMapping("/disposals/{disposalNo}/cancel")
    public DisposalDtos.Detail cancel(@PathVariable String disposalNo,
                                      @RequestBody(required = false) DisposalDtos.ActionRequest request,
                                      Authentication auth) {
        return service.cancel(disposalNo, request, auth.getName(), isAdmin(auth));
    }

    // ---------------- 확정 / 확정취소 (점장) ----------------

    @PostMapping("/disposals/{disposalNo}/confirm")
    public DisposalDtos.Detail confirm(@PathVariable String disposalNo,
                                       @RequestBody(required = false) DisposalDtos.ActionRequest request,
                                       Authentication auth) {
        requireAdmin(auth, "xác nhận phiếu hủy");
        return service.confirm(disposalNo, request, auth.getName());
    }

    @PostMapping("/disposals/{disposalNo}/cancel-confirm")
    public DisposalDtos.Detail cancelConfirm(@PathVariable String disposalNo,
                                             @RequestBody(required = false) DisposalDtos.ActionRequest request,
                                             Authentication auth) {
        requireAdmin(auth, "hủy xác nhận phiếu hủy");
        return service.cancelConfirm(disposalNo, request, auth.getName());
    }

    // ---------------- dữ liệu phụ trợ ----------------

    @GetMapping("/disposals/reasons")
    public List<DisposalDtos.ReasonInfo> reasons() {
        return service.reasons();
    }

    /** Quét barcode trên màn đăng ký: tên hàng, tồn, giá */
    @GetMapping("/inventory/{itemCode}")
    public DisposalDtos.ItemInfo inventory(@PathVariable String itemCode) {
        return service.item(itemCode);
    }

    /** 수불 của 1 mặt hàng, mới nhất trước */
    @GetMapping("/inventory/{itemCode}/transactions")
    public List<InventoryTransaction> history(@PathVariable String itemCode) {
        return service.history(itemCode);
    }

    @GetMapping("/store/closing")
    public DisposalDtos.ClosingInfo closing() {
        return service.closingInfo();
    }

    /** 일마감 (demo) */
    @PostMapping("/store/closing")
    public DisposalDtos.ClosingInfo close(@RequestBody DisposalDtos.ClosingRequest request, Authentication auth) {
        requireAdmin(auth, "chốt sổ");
        return service.close(request.closeDate(), auth.getName());
    }

    // ---------------- lỗi ----------------

    @ExceptionHandler(DisposalException.class)
    public ResponseEntity<DisposalDtos.Error> onBusinessError(DisposalException e) {
        return ResponseEntity.status(e.getStatus()).body(new DisposalDtos.Error(e.getCode(), e.getMessage(), e.getDetails()));
    }

    /** 2 người cùng thao tác 1 phiếu: người sau bị rollback */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<DisposalDtos.Error> onConcurrentUpdate(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new DisposalDtos.Error("STALE_DATA",
                "Dữ liệu vừa được người khác thay đổi. Vui lòng tải lại.", List.of()));
    }

    /** Body sai định dạng (ví dụ lý do hủy không có trong danh sách) */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<DisposalDtos.Error> onBadBody(Exception e) {
        return ResponseEntity.badRequest().body(new DisposalDtos.Error("VALIDATION",
                "Dữ liệu gửi lên không đúng định dạng", List.of()));
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ADMIN".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private static void requireAdmin(Authentication auth, String action) {
        if (!isAdmin(auth)) {
            throw new DisposalException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ 점장 (ADMIN) được " + action);
        }
    }

    private static DisposalStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        String s = status.trim().toUpperCase(Locale.ROOT);
        for (DisposalStatus st : DisposalStatus.values()) {
            if (st.name().equals(s) || st.getCode().equals(s)) {
                return st;
            }
        }
        throw new DisposalException(HttpStatus.BAD_REQUEST, "VALIDATION", "Trạng thái không hợp lệ: " + status);
    }
}
