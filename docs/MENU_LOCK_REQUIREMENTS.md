# Khóa Menu (메뉴 잠금) – Requirements & Implementation Notes

> Khóa các menu nhạy cảm của cửa hàng bằng mật khẩu 4 số (비밀번호), mở khóa bằng PIN, quên PIN thì reset qua OTP.

## 1. Danh sách menu (COMMON_CODE, group `LOCK_MENU`)

| code | Tên | attr1 (lock type) | Khi bị khóa |
|------|-----|-------------------|-------------|
| ORDER | 발주 | BLOCK | Popup "Menu này đã bị khóa. Vui lòng liên hệ 경영주", chặn vào màn hình |
| RETURN | 반품 | BLOCK | như trên |
| DISPOSAL | 폐기 | BLOCK | như trên |
| INVENTORY | 재고 | BLOCK | như trên |
| COST | 원가 | MASK | Vào được màn hình tra cứu, **Giá vốn / Tỷ suất lợi nhuận để trống** |

Thêm menu mới = thêm 1 row vào `common_code` (không sửa source). App Android dựng danh sách menu động từ API.

## 2. Luồng

```
[Khóa]   비번 설정 ? ── N → Màn hình OTP + PIN mới + Xác nhận → lưu DB ─┐
                    └─ Y ───────────────────────────────────────────────┴→ Popup "기능을 잠금 하시겠습니까?" → 확인 → lưu khóa + log LOCK

[Mở khóa] Popup nhập PIN 4 số ── "Quên mật khẩu / Reset PIN" → flow OTP
                    ├─ Sai < N lần → báo lỗi + log FAILED_TRY, cho nhập lại
                    ├─ Sai đủ N lần (mặc định 5) → khóa tạm, bắt buộc OTP để reset PIN
                    └─ Đúng → menu UNLOCK + log UNLOCK → nút đổi sang 잠금

[Bấm menu] check khóa ── không khóa → vào màn hình
                     ├─ BLOCK + khóa → popup + chặn
                     └─ MASK + khóa → vào màn hình, cột nhạy cảm để trống
```

## 3. API (`/api/menu-lock`, cần JWT)

| Method | Path | Mô tả |
|--------|------|-------|
| GET | `/status` | `pinSet`, `otpRequired`, danh sách menu + trạng thái khóa |
| GET | `/access/{menuCode}` | `allowed`, `masked`, `message` |
| POST | `/lock` | `{ "menuCodes": ["ORDER"] }` → 409 `PIN_NOT_SET` nếu chưa cài PIN |
| POST | `/unlock` | `{ "menuCode", "pin" }` → 401 `PIN_WRONG` (+`remainingAttempts`), 423 `OTP_REQUIRED` |
| POST | `/otp/request` | Gửi OTP (demo: in ra log, luôn là `123456`) |
| POST | `/pin` | `{ "otp", "newPin", "confirmPin" }` – cài lần đầu / reset PIN |
| GET | `/logs` | 50 log gần nhất của cửa hàng |

Màn hình nghiệp vụ (mock): `GET /api/store-menus/{menuCode}/data` → 423 `MENU_LOCKED` nếu menu BLOCK đang khóa.

## 4. Lưu ý kỹ thuật

- **Chặn ở backend**: menu BLOCK bị khóa → API nghiệp vụ trả 423 (`MenuLockService.assertAccessible`), không chỉ ẩn ở UI.
- **Data masking**: `/api/products/**` trả `null` cho `costPrice` / `marginRate` khi menu COST bị khóa → không leak qua F12 Network tab.
- **PIN** lưu BCrypt (`store_pin.pin_hash`), áp dụng theo cửa hàng (`users.store_id`).
- **Đếm sai PIN**: `@Transactional(noRollbackFor = MenuLockException.class)` để vẫn lưu `failCount` + log FAILED_TRY khi trả lỗi.
- **Log table** `menu_lock_log`: `log_id, store_id, menu_code, action_type (LOCK/UNLOCK/FAILED_TRY/PIN_SET), actor_id, created_at, ip_address` (IP lấy từ `X-Forwarded-For` khi chạy sau proxy Render).
- Cấu hình: `app.menu-lock.max-fail=5`, `app.otp.demo-mode=true`, `app.otp.ttl-seconds=180`.
- TODO production: tích hợp SMS thật cho OTP, tắt `demo-mode`, lưu OTP vào DB/Redis thay vì in-memory.

## 5. Test

`backend/src/test/java/com/example/andemo/MenuLockFlowTest.java` – chạy `mvn test`.
