package com.example.andemo.disposal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

/**
 * API phiếu hủy + tồn kho (Task 17, 18). Xem docs/DISPOSAL_TASK_17_18.md.
 *
 * Xem: mọi user đã đăng nhập. Xác nhận: chỉ ADMIN (giả định, cần BA xác nhận quyền).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DisposalController {

    private final DisposalService service;

    /** status: REQUESTED / CONFIRMED / CANCELLED; bỏ trống hoặc ALL = tất cả */
    @GetMapping("/disposals")
    public List<DisposalDtos.Summary> list(@RequestParam(required = false) String status) {
        return service.list(parseStatus(status));
    }

    @GetMapping("/disposals/{disposalNo}")
    public DisposalDtos.Detail detail(@PathVariable String disposalNo) {
        return service.detail(disposalNo);
    }

    @PostMapping("/disposals/{disposalNo}/confirm")
    public DisposalDtos.Detail confirm(@PathVariable String disposalNo,
                                       @RequestBody(required = false) DisposalDtos.ConfirmRequest request,
                                       Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ADMIN".equals(a.getAuthority()));
        if (!admin) {
            throw new DisposalException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ quản lý (ADMIN) được xác nhận phiếu hủy");
        }
        return service.confirm(disposalNo, request == null ? null : request.version(), authentication.getName());
    }

    @GetMapping("/inventory/{itemCode}")
    public InventoryItem inventory(@PathVariable String itemCode) {
        return service.inventory(itemCode);
    }

    @GetMapping("/inventory/{itemCode}/transactions")
    public List<InventoryTransaction> history(@PathVariable String itemCode) {
        return service.history(itemCode);
    }

    @ExceptionHandler(DisposalException.class)
    public ResponseEntity<DisposalDtos.Error> onBusinessError(DisposalException e) {
        return ResponseEntity.status(e.getStatus()).body(new DisposalDtos.Error(e.getCode(), e.getMessage(), e.getDetails()));
    }

    /** 2 người xác nhận cùng lúc: người sau bị rollback */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<DisposalDtos.Error> onConcurrentUpdate(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new DisposalDtos.Error("STALE_DATA",
                "Dữ liệu vừa được người khác thay đổi. Vui lòng tải lại.", List.of()));
    }

    private static DisposalStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return DisposalStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DisposalException(HttpStatus.BAD_REQUEST, "BAD_STATUS", "Trạng thái không hợp lệ: " + status);
        }
    }
}
