# Khóa Menu – Requirements & Implementation Notes

> Khóa các menu nhạy cảm của cửa hàng bằng mật khẩu 4 số, mở khóa bằng PIN, quên PIN thì reset qua OTP.

## 1. Danh sách menu (COMMON_CODE, group `LOCK_MENU`)

| code | Tên | attr1 (lock type) | Khi bị khóa |
|------|-----|-------------------|-------------|
| ORDER | Đặt hàng | BLOCK | Popup "Menu này đã bị khóa. Vui lòng liên hệ chủ cửa hàng", chặn vào màn hình |
| RETURN | Trả hàng | BLOCK | như trên |
| DISPOSAL | Hủy hàng | BLOCK | như trên |
| INVENTORY | Tồn kho | BLOCK | như trên |
| COST | Giá vốn | MASK | Vào được màn hình tra cứu, **Giá vốn / Tỷ suất lợi nhuận để trống** |

Thêm menu mới = thêm 1 row vào `common_code` (không sửa source). App Android dựng danh sách menu động từ API.

## 2. Luồng

Nút **Khóa / Mở khóa** nằm trên title (thanh tiêu đề "Menu chính" và màn Khóa Menu), khóa / mở **tất cả** menu cùng lúc.

```
[Khóa]   Đã cài mật khẩu? ── N → Popup thiết lập mật khẩu ──────────────────────┐
                         └─ Y ──────────────────────────────────────────────────┴→ Popup "Bạn có muốn khóa chức năng không?
                                                                                     (Đặt hàng, Trả hàng, Hủy hàng, Tồn kho, Giá vốn)"
                                                                                     Đóng / Xác nhận → khóa tất cả + log LOCK → nút đổi sang "Mở khóa"

[Popup thiết lập mật khẩu]
   (2) Yêu cầu mã xác thực → popup "Mã cửa hàng / Tên cửa hàng / SĐT chủ cửa hàng (010-****-8110)
                               Bạn có muốn yêu cầu xác thực OTP không?" Hủy / Xác nhận → gửi OTP
   (3) Nhập OTP 6 số → Xác nhận (POST /otp/verify) → mở ô nhập mật khẩu
   (4) Nhập mật khẩu 4 số   (5) Xác nhận mật khẩu
   Đóng / Xác nhận → lưu DB + log PIN_SET

[Mở khóa] Popup: Mật khẩu [••••] [Mở khóa] — Đóng / Đặt lại mật khẩu (→ popup thiết lập mật khẩu)
                    ├─ Sai < N lần → báo lỗi + log FAILED_TRY, cho nhập lại
                    ├─ Sai đủ N lần (mặc định 5) → khóa tạm, bắt buộc Đặt lại mật khẩu qua OTP
                    └─ Đúng → mở tất cả menu + log UNLOCK → nút đổi sang "Khóa"

[Bấm menu] check khóa ── không khóa → vào màn hình
                     ├─ BLOCK + khóa → popup + chặn
                     └─ MASK + khóa → vào màn hình, cột Giá vốn / Tỷ suất lợi nhuận để trống
```

## 3. Thiết kế bảng (DB)

> Hiện chạy H2 in-memory, bảng do JPA tạo (`ddl-auto=update`). Tên cột thực tế dạng snake_case.

### 3.1 `common_code` – mã dùng chung (danh sách menu có thể khóa)

| Cột | Kiểu | Null | Mô tả |
|-----|------|------|-------|
| id | BIGINT, PK, auto increment | N | |
| group_code | VARCHAR | N | Nhóm mã, vd `LOCK_MENU` |
| code | VARCHAR | N | Mã menu: `ORDER`, `RETURN`, `DISPOSAL`, `INVENTORY`, `COST` |
| code_name | VARCHAR | N | Tên hiển thị, vd `Đặt hàng` |
| attr1 | VARCHAR | Y | `BLOCK` (chặn truy cập) / `MASK` (ẩn dữ liệu nhạy cảm) |
| sort_order | INT | N | Thứ tự hiển thị |
| use_yn | BOOLEAN | N | `false` = ẩn menu, không cho khóa |

- UNIQUE (`group_code`, `code`)

### 3.2 `menu_lock` – trạng thái khóa menu theo cửa hàng

| Cột | Kiểu | Null | Mô tả |
|-----|------|------|-------|
| id | BIGINT, PK, auto increment | N | |
| store_id | VARCHAR | N | Mã cửa hàng (`users.store_id`) |
| menu_code | VARCHAR | N | = `common_code.code` (group `LOCK_MENU`) |
| locked | BOOLEAN | N | `true` = đang khóa |
| updated_by | VARCHAR | Y | Username thao tác gần nhất |
| updated_at | TIMESTAMP | Y | Thời điểm cập nhật gần nhất |

- UNIQUE (`store_id`, `menu_code`)
- Không có row = chưa từng khóa = **không khóa**

### 3.3 `store_pin` – mật khẩu 4 số của cửa hàng

| Cột | Kiểu | Null | Mô tả |
|-----|------|------|-------|
| store_id | VARCHAR, PK | N | Mỗi cửa hàng 1 PIN |
| pin_hash | VARCHAR | N | BCrypt hash của PIN, **không lưu plain text** |
| fail_count | INT | N | Số lần nhập sai liên tiếp, reset về 0 khi đúng / cài lại |
| otp_required | BOOLEAN | N | `true` khi sai ≥ `max-fail` → phải OTP + cài lại PIN |
| updated_at | TIMESTAMP | Y | Lần cài / đổi PIN gần nhất |

- Không có row = **chưa cài mật khẩu** (đã cài mật khẩu = N)

### 3.4 `menu_lock_log` – lịch sử thao tác

| Cột | Kiểu | Null | Mô tả |
|-----|------|------|-------|
| log_id | BIGINT, PK, auto increment | N | |
| store_id | VARCHAR | N | |
| menu_code | VARCHAR | Y | `null` với `PIN_SET` |
| action_type | VARCHAR | N | `LOCK` / `UNLOCK` / `FAILED_TRY` / `PIN_SET` |
| actor_id | VARCHAR | Y | Username người thao tác |
| created_at | TIMESTAMP | N | |
| ip_address | VARCHAR | Y | Lấy từ `X-Forwarded-For`, fallback `remoteAddr` |

- Gợi ý index khi lên DB thật: (`store_id`, `log_id` DESC), (`store_id`, `created_at`)

### 3.5 `users` – thêm cột

| Cột | Kiểu | Null | Mô tả |
|-----|------|------|-------|
| store_id | VARCHAR | Y | Cửa hàng của user (seed: admin/user → `STORE001`, user2 → `STORE002`) |

### 3.6 Quan hệ

```
users.store_id ──┬── store_pin.store_id        (1 : 0..1)
                 ├── menu_lock.store_id        (1 : N)
                 └── menu_lock_log.store_id    (1 : N)

common_code(group=LOCK_MENU).code ──┬── menu_lock.menu_code      (1 : N)
                                    └── menu_lock_log.menu_code  (1 : N)
```

### 3.7 DDL tham khảo (khi chuyển sang Postgres/MySQL)

```sql
CREATE TABLE common_code (
    id          BIGSERIAL PRIMARY KEY,
    group_code  VARCHAR(50)  NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    code_name   VARCHAR(100) NOT NULL,
    attr1       VARCHAR(50),
    sort_order  INT          NOT NULL DEFAULT 0,
    use_yn      BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE (group_code, code)
);

CREATE TABLE menu_lock (
    id          BIGSERIAL PRIMARY KEY,
    store_id    VARCHAR(50) NOT NULL,
    menu_code   VARCHAR(50) NOT NULL,
    locked      BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_by  VARCHAR(50),
    updated_at  TIMESTAMP,
    UNIQUE (store_id, menu_code)
);

CREATE TABLE store_pin (
    store_id      VARCHAR(50) PRIMARY KEY,
    pin_hash      VARCHAR(100) NOT NULL,
    fail_count    INT          NOT NULL DEFAULT 0,
    otp_required  BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at    TIMESTAMP
);

CREATE TABLE menu_lock_log (
    log_id       BIGSERIAL PRIMARY KEY,
    store_id     VARCHAR(50) NOT NULL,
    menu_code    VARCHAR(50),
    action_type  VARCHAR(20) NOT NULL,
    actor_id     VARCHAR(50),
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address   VARCHAR(45)
);
CREATE INDEX idx_menu_lock_log_store ON menu_lock_log (store_id, log_id DESC);

ALTER TABLE users ADD COLUMN store_id VARCHAR(50);

INSERT INTO common_code (group_code, code, code_name, attr1, sort_order) VALUES
 ('LOCK_MENU', 'ORDER',     'Đặt hàng',  'BLOCK', 1),
 ('LOCK_MENU', 'RETURN',    'Trả hàng',  'BLOCK', 2),
 ('LOCK_MENU', 'DISPOSAL',  'Hủy hàng',  'BLOCK', 3),
 ('LOCK_MENU', 'INVENTORY', 'Tồn kho',   'BLOCK', 4),
 ('LOCK_MENU', 'COST',      'Giá vốn',   'MASK',  5);
```

## 4. API (`/api/menu-lock`, cần JWT)

| Method | Path | Mô tả |
|--------|------|-------|
| GET | `/status` | `storeName`, `ownerPhone` (đã che), `pinSet`, `otpRequired`, danh sách menu + trạng thái khóa |
| GET | `/access/{menuCode}` | `allowed`, `masked`, `message` |
| POST | `/lock` | `{ "menuCodes": ["ORDER"] }` → 409 `PIN_NOT_SET` nếu chưa cài PIN |
| POST | `/unlock` | `{ "menuCode", "pin" }` – bỏ `menuCode` = mở tất cả → 401 `PIN_WRONG` (+`remainingAttempts`), 423 `OTP_REQUIRED` |
| POST | `/otp/request` | Gửi OTP (demo: in ra log, luôn là `123456`) → 429 `OTP_TOO_FREQUENT` nếu yêu cầu lại trong 60 giây |
| POST | `/otp/verify` | `{ "otp" }` – kiểm tra OTP (chưa tiêu thụ) trước khi cho nhập mật khẩu mới → 400 `OTP_INVALID` (+`remainingAttempts`), `OTP_ATTEMPTS_EXCEEDED`, `OTP_EXPIRED` |
| POST | `/pin` | `{ "otp", "newPin", "confirmPin" }` – cài lần đầu / reset PIN |
| GET | `/logs` | 50 log gần nhất của cửa hàng |

Màn hình nghiệp vụ (mock): `GET /api/store-menus/{menuCode}/data` → 423 `MENU_LOCKED` nếu menu BLOCK đang khóa.

## 5. Lưu ý kỹ thuật

- **Chặn ở backend**: menu BLOCK bị khóa → API nghiệp vụ trả 423 (`MenuLockService.assertAccessible`), không chỉ ẩn ở UI.
- **Data masking**: `/api/products/**` trả `null` cho `costPrice` / `marginRate` khi menu COST bị khóa → không leak qua F12 Network tab.
- **PIN** lưu BCrypt (`store_pin.pin_hash`), áp dụng theo cửa hàng (`users.store_id`).
- **Đếm sai PIN**: `@Transactional(noRollbackFor = MenuLockException.class)` để vẫn lưu `failCount` + log FAILED_TRY khi trả lỗi.
- **Log table** `menu_lock_log`: `log_id, store_id, menu_code, action_type (LOCK/UNLOCK/FAILED_TRY/PIN_SET), actor_id, created_at, ip_address` (IP lấy từ `X-Forwarded-For` khi chạy sau proxy Render).
- **OTP**: lưu trong RAM theo cửa hàng, hiệu lực 180 giây, dùng 1 lần (bị xóa khi đặt PIN thành công). Phải yêu cầu mã trước khi nhập (không có mã → `OTP_EXPIRED`).
  - Nhập sai (ở `/otp/verify` hoặc `/pin`, cộng dồn) đủ 5 lần → hủy mã (`OTP_ATTEMPTS_EXCEEDED`), phải yêu cầu mã mới.
  - Yêu cầu mã cách nhau tối thiểu 60 giây (`OTP_TOO_FREQUENT`) → chặn dò mã bằng cách xin mã mới liên tục.
- Cấu hình: `app.menu-lock.max-fail=5`, `app.otp.demo-mode=true`, `app.otp.ttl-seconds=180`, `app.otp.max-attempts=5`, `app.otp.resend-seconds=60`.
- TODO production: tích hợp SMS thật cho OTP, tắt `demo-mode`, lưu OTP vào DB/Redis thay vì in-memory.

## 6. Test

`backend/src/test/java/com/example/andemo/MenuLockFlowTest.java`, `OtpLimitTest.java` – chạy `mvn test`.
