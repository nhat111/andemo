package com.example.andemo.service;

import com.example.andemo.dto.MenuAccessResponse;
import com.example.andemo.dto.MenuLockLogDto;
import com.example.andemo.dto.MenuLockStatusResponse;
import com.example.andemo.dto.MenuStatusDto;
import com.example.andemo.entity.CommonCode;
import com.example.andemo.entity.MenuLock;
import com.example.andemo.entity.MenuLockLog;
import com.example.andemo.entity.StorePin;
import com.example.andemo.entity.User;
import com.example.andemo.exception.MenuLockException;
import com.example.andemo.repository.CommonCodeRepository;
import com.example.andemo.repository.MenuLockLogRepository;
import com.example.andemo.repository.MenuLockRepository;
import com.example.andemo.repository.StorePinRepository;
import com.example.andemo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Khóa / mở khóa menu theo cửa hàng.
 * Danh sách menu lấy từ COMMON_CODE (group LOCK_MENU) → thêm menu mới không cần sửa code.
 */
@Service
@RequiredArgsConstructor
public class MenuLockService {

    public static final String GROUP_LOCK_MENU = "LOCK_MENU";
    public static final String LOCK_TYPE_BLOCK = "BLOCK";
    public static final String LOCK_TYPE_MASK = "MASK";

    public static final String ACTION_LOCK = "LOCK";
    public static final String ACTION_UNLOCK = "UNLOCK";
    public static final String ACTION_FAILED_TRY = "FAILED_TRY";
    public static final String ACTION_PIN_SET = "PIN_SET";

    public static final String LOCKED_MESSAGE = "Menu này đã bị khóa. Vui lòng liên hệ chủ cửa hàng";

    private final CommonCodeRepository commonCodeRepository;
    private final MenuLockRepository menuLockRepository;
    private final StorePinRepository storePinRepository;
    private final MenuLockLogRepository logRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final StoreInfoService storeInfoService;

    @Value("${app.menu-lock.max-fail:5}")
    private int maxFail;

    /** Lấy storeId của user đang login */
    public String resolveStoreId(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getStoreId() != null ? user.getStoreId() : "STORE001";
    }

    // ===================== Trạng thái / kiểm tra truy cập =====================

    public MenuLockStatusResponse getStatus(String storeId) {
        StorePin pin = storePinRepository.findById(storeId).orElse(null);
        return new MenuLockStatusResponse(
                storeId,
                storeInfoService.storeName(storeId),
                storeInfoService.maskedOwnerPhone(storeId),
                pin != null,
                pin != null && pin.isOtpRequired(),
                getMenus(storeId)
        );
    }

    public List<MenuStatusDto> getMenus(String storeId) {
        Map<String, MenuLock> locks = menuLockRepository.findByStoreId(storeId).stream()
                .collect(Collectors.toMap(MenuLock::getMenuCode, Function.identity()));

        return commonCodeRepository.findByGroupCodeAndUseYnTrueOrderBySortOrder(GROUP_LOCK_MENU).stream()
                .map(c -> {
                    MenuLock lock = locks.get(c.getCode());
                    return new MenuStatusDto(c.getCode(), c.getCodeName(), lockTypeOf(c),
                            lock != null && lock.isLocked());
                })
                .collect(Collectors.toList());
    }

    public boolean isLocked(String storeId, String menuCode) {
        return menuLockRepository.findByStoreIdAndMenuCode(storeId, menuCode)
                .map(MenuLock::isLocked)
                .orElse(false);
    }

    /**
     * Flow "Người dùng bấm vào một Menu bất kỳ":
     * - Không khóa → cho vào
     * - BLOCK + khóa → chặn, hiện popup
     * - MASK + khóa → cho vào nhưng ẩn Giá vốn / Tỷ suất lợi nhuận
     */
    public MenuAccessResponse checkAccess(String storeId, String menuCode) {
        CommonCode menu = getMenuOrThrow(menuCode);
        boolean locked = isLocked(storeId, menuCode);

        if (!locked) {
            return new MenuAccessResponse(menuCode, true, false, null);
        }
        if (LOCK_TYPE_MASK.equals(lockTypeOf(menu))) {
            return new MenuAccessResponse(menuCode, true, true,
                    "Giá vốn / Tỷ suất lợi nhuận đang bị ẩn");
        }
        return new MenuAccessResponse(menuCode, false, false, LOCKED_MESSAGE);
    }

    /** Dùng ở các API nghiệp vụ (đặt hàng / trả hàng / hủy hàng / tồn kho) để chặn ở backend, không chỉ ở UI */
    public void assertAccessible(String storeId, String menuCode) {
        MenuAccessResponse access = checkAccess(storeId, menuCode);
        if (!access.isAllowed()) {
            throw new MenuLockException(HttpStatus.LOCKED, MenuLockException.MENU_LOCKED, LOCKED_MESSAGE);
        }
    }

    // ===================== Khóa =====================

    /** Flow "Khóa": phải cài PIN trước, sau đó lưu trạng thái khóa */
    @Transactional
    public List<MenuStatusDto> lock(String storeId, List<String> menuCodes, String actor, String ip) {
        if (!storePinRepository.existsById(storeId)) {
            throw new MenuLockException(HttpStatus.CONFLICT, MenuLockException.PIN_NOT_SET,
                    "Chưa cài mật khẩu 4 số. Vui lòng thiết lập mật khẩu trước khi khóa");
        }
        if (menuCodes == null || menuCodes.isEmpty()) {
            throw new MenuLockException(HttpStatus.BAD_REQUEST, MenuLockException.MENU_NOT_FOUND,
                    "Chưa chọn menu cần khóa");
        }

        for (String code : menuCodes) {
            getMenuOrThrow(code);
            if (isLocked(storeId, code)) continue;
            saveLockState(storeId, code, true, actor);
            writeLog(storeId, code, ACTION_LOCK, actor, ip);
        }
        return getMenus(storeId);
    }

    // ===================== Mở khóa =====================

    /**
     * Flow "Mở khóa": check PIN với DB. menuCode null → mở tất cả menu đang khóa.
     * Sai < N lần → báo lỗi + log FAILED_TRY. Sai đủ N lần → bắt buộc OTP.
     * Không rollback khi ném MenuLockException để vẫn lưu failCount + log.
     */
    @Transactional(noRollbackFor = MenuLockException.class)
    public List<MenuStatusDto> unlock(String storeId, String menuCode, String pin, String actor, String ip) {
        boolean unlockAll = menuCode == null || menuCode.isBlank();
        if (unlockAll) menuCode = null;
        else getMenuOrThrow(menuCode);

        StorePin storePin = storePinRepository.findById(storeId)
                .orElseThrow(() -> new MenuLockException(HttpStatus.CONFLICT, MenuLockException.PIN_NOT_SET,
                        "Chưa cài mật khẩu 4 số"));

        if (storePin.isOtpRequired()) {
            throw new MenuLockException(HttpStatus.LOCKED, MenuLockException.OTP_REQUIRED,
                    "Nhập sai quá " + maxFail + " lần. Vui lòng xác thực OTP và cài lại mật khẩu");
        }

        if (pin == null || !passwordEncoder.matches(pin, storePin.getPinHash())) {
            storePin.setFailCount(storePin.getFailCount() + 1);
            writeLog(storeId, menuCode, ACTION_FAILED_TRY, actor, ip);

            if (storePin.getFailCount() >= maxFail) {
                storePin.setOtpRequired(true);
                storePinRepository.save(storePin);
                throw new MenuLockException(HttpStatus.LOCKED, MenuLockException.OTP_REQUIRED,
                        "Nhập sai quá " + maxFail + " lần. Vui lòng xác thực OTP và cài lại mật khẩu");
            }
            storePinRepository.save(storePin);
            int remaining = maxFail - storePin.getFailCount();
            throw new MenuLockException(HttpStatus.UNAUTHORIZED, MenuLockException.PIN_WRONG,
                    "Sai mật khẩu. Còn " + remaining + " lần thử", remaining);
        }

        storePin.setFailCount(0);
        storePinRepository.save(storePin);

        List<String> targets = unlockAll
                ? getMenus(storeId).stream().map(MenuStatusDto::getMenuCode).collect(Collectors.toList())
                : List.of(menuCode);
        for (String code : targets) {
            if (!isLocked(storeId, code)) continue;
            saveLockState(storeId, code, false, actor);
            writeLog(storeId, code, ACTION_UNLOCK, actor, ip);
        }
        return getMenus(storeId);
    }

    // ===================== PIN / OTP =====================

    public void requestOtp(String storeId) {
        otpService.issue(storeId);
    }

    /** Bước ③: xác thực OTP trước khi cho nhập mật khẩu mới (chưa tiêu thụ OTP) */
    public void verifyOtp(String storeId, String otp) {
        otpService.check(storeId, otp);
    }

    /** Thiết lập lần đầu hoặc reset PIN (Quên mật khẩu): OTP + PIN mới + xác nhận */
    @Transactional
    public void setupPin(String storeId, String otp, String newPin, String confirmPin, String actor, String ip) {
        if (newPin == null || !newPin.matches("\\d{4}")) {
            throw new MenuLockException(HttpStatus.BAD_REQUEST, MenuLockException.PIN_INVALID_FORMAT,
                    "Mật khẩu phải gồm đúng 4 chữ số");
        }
        if (!newPin.equals(confirmPin)) {
            throw new MenuLockException(HttpStatus.BAD_REQUEST, MenuLockException.PIN_MISMATCH,
                    "Mật khẩu xác nhận không khớp");
        }
        otpService.verify(storeId, otp);

        StorePin storePin = storePinRepository.findById(storeId).orElseGet(StorePin::new);
        storePin.setStoreId(storeId);
        storePin.setPinHash(passwordEncoder.encode(newPin));
        storePin.setFailCount(0);
        storePin.setOtpRequired(false);
        storePin.setUpdatedAt(LocalDateTime.now());
        storePinRepository.save(storePin);

        writeLog(storeId, null, ACTION_PIN_SET, actor, ip);
    }

    public List<MenuLockLogDto> getLogs(String storeId) {
        return logRepository.findTop50ByStoreIdOrderByLogIdDesc(storeId).stream()
                .map(l -> new MenuLockLogDto(l.getLogId(), l.getStoreId(), l.getMenuCode(), l.getActionType(),
                        l.getActorId(), l.getCreatedAt().toString(), l.getIpAddress()))
                .collect(Collectors.toList());
    }

    // ===================== helper =====================

    private CommonCode getMenuOrThrow(String menuCode) {
        return commonCodeRepository.findByGroupCodeAndCodeAndUseYnTrue(GROUP_LOCK_MENU, menuCode)
                .orElseThrow(() -> new MenuLockException(HttpStatus.NOT_FOUND, MenuLockException.MENU_NOT_FOUND,
                        "Không tìm thấy menu: " + menuCode));
    }

    private String lockTypeOf(CommonCode menu) {
        return LOCK_TYPE_MASK.equals(menu.getAttr1()) ? LOCK_TYPE_MASK : LOCK_TYPE_BLOCK;
    }

    private void saveLockState(String storeId, String menuCode, boolean locked, String actor) {
        MenuLock lock = menuLockRepository.findByStoreIdAndMenuCode(storeId, menuCode).orElseGet(() -> {
            MenuLock l = new MenuLock();
            l.setStoreId(storeId);
            l.setMenuCode(menuCode);
            return l;
        });
        lock.setLocked(locked);
        lock.setUpdatedBy(actor);
        lock.setUpdatedAt(LocalDateTime.now());
        menuLockRepository.save(lock);
    }

    private void writeLog(String storeId, String menuCode, String action, String actor, String ip) {
        MenuLockLog log = new MenuLockLog();
        log.setStoreId(storeId);
        log.setMenuCode(menuCode);
        log.setActionType(action);
        log.setActorId(actor);
        log.setCreatedAt(LocalDateTime.now());
        log.setIpAddress(ip);
        logRepository.save(log);
    }
}
