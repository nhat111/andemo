# Task 17–18: Xác nhận phiếu hủy hàng và cập nhật tồn kho

> Nhánh: `claude/android-nexacro-migration`. Cập nhật: 2026-09-29.
> Đây là **bản mẫu dựa trên giả định** (mục 4) để học và làm khung; khi có spec / mã nguồn thật phải đối chiếu lại các câu hỏi ở mục 5.

---

## 1. Requirement nhận được

| # | Loại | Trạng thái | Chức năng | Hạng mục | Nội dung |
|---|---|---|---|---|---|
| 17 | Function | New | Add disposal item registration/inquiry/edit/cancellation functions (scrap items) | 1/ Confirm disposal items | Add Disposal Inquiry Screen · Add Disposal Detail View · Add Confirm Button · Disposal Status Validation |
| 18 | Function | New | (như trên) | 2/ Update inventory items | Inventory Deduction Processing · Inventory Quantity Synchronization · Inventory Transaction History Update · Validation of Available Quantity |

Các cột số `1, 3` và `1, 3, 4.6` chưa rõ nghĩa (độ ưu tiên? độ khó? man-day?). Xem câu hỏi Q1.

Tiêu đề chức năng có "registration / edit / cancellation" nhưng 2 dòng này chỉ gồm **xác nhận** và **trừ tồn**. Đăng ký / sửa / hủy phiếu có thể nằm ở các dòng khác của task list. Demo chỉ làm phần 17–18, dữ liệu phiếu được tạo sẵn.

---

## 2. Hiểu nghiệp vụ

```
 Nhân viên kho                    Quản lý                                   Hệ thống
 ──────────────                   ─────────────────────                     ──────────────────────────────
 Lập phiếu hủy (REQUESTED) ─────► Tra cứu phiếu chờ xác nhận   (17)
                                  Xem chi tiết: mặt hàng, SL hủy,
                                  tồn khả dụng hiện tại        (17)
                                  Bấm "Xác nhận"               (17) ──────► Kiểm tra trạng thái     (17)
                                                                            Khóa tồn kho các mặt hàng (18 Sync)
                                                                            Kiểm tra SL khả dụng     (18 Validation)
                                                                            Trừ tồn                  (18 Deduction)
                                                                            Ghi lịch sử tồn kho      (18 History)
                                                                            Phiếu → CONFIRMED
                                                                            (tất cả trong 1 transaction DB)
```

### Trạng thái phiếu

```
REQUESTED ──(xác nhận: trừ tồn)──► CONFIRMED   (không sửa, không hủy được nữa)
    │
    └──────(hủy phiếu)───────────► CANCELLED   (thuộc task "cancellation", demo chỉ có dữ liệu mẫu)
```

---

## 3. Đối chiếu từng hạng mục với code

| Hạng mục | Làm gì | Backend | Android | Test |
|---|---|---|---|---|
| **17 – Disposal Inquiry Screen** | Danh sách phiếu, lọc theo trạng thái (mặc định Chờ xác nhận), mới nhất trước | `GET /api/disposals?status=` → `DisposalService.list` | `disposal/DisposalListActivity` + `DisposalListAdapter` | `listFiltersByStatus` |
| **17 – Disposal Detail View** | Header + các dòng; mỗi dòng có tồn thực tế, **tồn khả dụng**, đánh dấu đỏ dòng thiếu; hiện lý do không xác nhận được | `GET /api/disposals/{no}` → `DisposalService.detail` (`confirmable`, `issues`, `sufficient`) | `disposal/DisposalDetailActivity` + `DisposalLineAdapter` | `detailShowsAvailableQtyAndWhyNotConfirmable` |
| **17 – Confirm Button** | Nút chỉ hiện cho ADMIN khi phiếu REQUESTED, chỉ bật khi đủ điều kiện; hộp thoại xác nhận; chống bấm 2 lần | `POST /api/disposals/{no}/confirm` body `{version}` | `DisposalDetailActivity.askConfirm / confirm` | `confirmDeductsInventoryAndWritesHistory`, `onlyAdminCanConfirm` |
| **17 – Disposal Status Validation** | Chỉ REQUESTED mới xác nhận được; phiếu đã xác nhận / đã hủy → 409; dữ liệu đang xem đã cũ (version khác) → 409 | `DisposalService.validateConfirmable`, kiểm tra `version` | Ẩn/tắt nút + hiển thị lỗi server trả về, nút "Tải lại" | `confirmTwiceIsRejectedAndDeductsOnlyOnce`, `cancelledDisposalCannotBeConfirmed`, `staleVersionIsRejected` |
| **18 – Inventory Deduction Processing** | Trừ `onHandQty` từng dòng, cùng transaction với việc đổi trạng thái phiếu | `DisposalService.confirm` | | `confirmDeductsInventoryAndWritesHistory` |
| **18 – Inventory Quantity Synchronization** | 2 người / 2 nghiệp vụ cùng sửa tồn 1 mặt hàng không làm sai số: khóa dòng tồn kho (`SELECT … FOR UPDATE`) theo thứ tự mã hàng; phiếu có `@Version` | `InventoryItemRepository.lockByItemCodes`, `@Version` trên `DisposalRequest` và `InventoryItem` | | `twoManagersConfirmingAtTheSameTimeDeductOnlyOnce` |
| **18 – Inventory Transaction History Update** | Mỗi dòng trừ tồn ghi 1 bản ghi: loại DISPOSAL, số lượng (âm), tồn trước / sau, số phiếu + dòng, người, thời điểm. Chỉ thêm, không sửa / xóa | Bảng `inventory_transaction`, `GET /api/inventory/{code}/transactions` | | `confirmDeductsInventoryAndWritesHistory` |
| **18 – Validation of Available Quantity** | Khả dụng = tồn − đã giữ chỗ. Cộng dồn nếu 1 mặt hàng nằm nhiều dòng. Kiểm tra **hết** các dòng rồi mới trừ; thiếu bất kỳ dòng nào → **không trừ gì**, trả danh sách mọi dòng thiếu | `DisposalService.shortages` → 422 `INSUFFICIENT_QTY` | Dòng thiếu tô đỏ trước khi bấm; lỗi 422 hiện đủ các dòng | `insufficientQtyRejectsWholeDisposalWithoutAnyChange` |

Backend: package `backend/.../disposal/`. Test: `backend/src/test/.../DisposalIntegrationTest.java` (10 test).

### Dữ liệu

| Bảng | Cột chính |
|---|---|
| `disposal_request` | `disposal_no` (PK), `warehouse_code`, `status`, `reason`, `requested_by/at`, `confirmed_by/at`, `version` |
| `disposal_item` | `id`, `disposal_no` (FK), `line_no`, `item_code`, `qty`, `reason_code` |
| `inventory_item` | `item_code` (PK), `item_name`, `warehouse_code`, `on_hand_qty`, `allocated_qty`, `version` |
| `inventory_transaction` | `id`, `item_code`, `tx_type`, `qty_change`, `before_qty`, `after_qty`, `ref_no`, `ref_line_no`, `created_by/at` |

### API

| Method | URL | Quyền | Kết quả |
|---|---|---|---|
| GET | `/api/disposals?status=REQUESTED\|CONFIRMED\|CANCELLED\|ALL` | Đăng nhập | Danh sách (số phiếu, trạng thái, lý do, số dòng, tổng SL, người / thời điểm yêu cầu) |
| GET | `/api/disposals/{no}` | Đăng nhập | Chi tiết + `confirmable`, `issues`, từng dòng có `availableQty`, `sufficient`, `version` |
| POST | `/api/disposals/{no}/confirm` body `{"version": 0}` | ADMIN | Chi tiết sau khi xác nhận |
| GET | `/api/inventory/{itemCode}` | Đăng nhập | Tồn hiện tại |
| GET | `/api/inventory/{itemCode}/transactions` | Đăng nhập | Lịch sử biến động, mới nhất trước |

Lỗi trả dạng `{"code": "...", "message": "...", "details": [...]}`:

| HTTP | code | Khi nào |
|---|---|---|
| 404 | `NOT_FOUND` | Không có phiếu |
| 409 | `INVALID_STATUS` | Phiếu đã xác nhận / đã hủy |
| 409 | `STALE_DATA` | `version` gửi lên khác hiện tại, hoặc người khác vừa xác nhận cùng lúc |
| 422 | `INSUFFICIENT_QTY` | Thiếu tồn khả dụng (`details` liệt kê từng mặt hàng) |
| 403 | `FORBIDDEN` | Không phải ADMIN |

---

## 4. Giả định đang dùng (cần BA xác nhận)

| # | Giả định |
|---|---|
| A1 | Chỉ quản lý (role ADMIN) được xác nhận; mọi người đăng nhập đều xem được |
| A2 | Xác nhận = trừ tồn **ngay** (không có bước "đã hủy thực tế" riêng) |
| A3 | **Tất cả hoặc không**: 1 dòng thiếu tồn thì cả phiếu không được xác nhận (không xác nhận từng phần) |
| A4 | Tồn khả dụng = tồn thực tế − đã giữ chỗ cho nghiệp vụ khác |
| A5 | 1 kho, không quản lý lô / hạn dùng / vị trí (bin) / serial |
| A6 | Phiếu đã xác nhận không hủy được; muốn đảo phải có nghiệp vụ điều chỉnh riêng (ghi giao dịch cộng lại) |
| A7 | Lịch sử tồn kho chỉ thêm, không sửa / xóa |
| A8 | Tồn kho nằm trong DB của hệ thống này (không phải gọi sang ERP khác) |

---

## 5. Câu hỏi cho BA / khách hàng

| # | Câu hỏi | Ảnh hưởng |
|---|---|---|
| Q1 | Các cột `1, 3` và `1, 3, 4.6` trong task list nghĩa là gì (ưu tiên, độ khó, man-day)? | Lập kế hoạch |
| Q2 | Ai được xác nhận? Có duyệt nhiều cấp không (trưởng kho → quản lý cửa hàng)? | A1, quyền, trạng thái thêm |
| Q3 | Có cho xác nhận **một phần** (bỏ dòng thiếu tồn) không? | A3 |
| Q4 | "Available quantity" tính thế nào trong hệ thống hiện tại (trừ hàng đã giữ chỗ, hàng đang kiểm kê, hàng chờ nhập…)? | A4 |
| Q5 | Có quản lý theo lô / hạn dùng / vị trí / serial không? Hủy hàng hết hạn thường phải chỉ đúng lô | A5, cấu trúc bảng |
| Q6 | Xác nhận xong có hủy / đảo được không? Nếu được thì ghi lịch sử thế nào? | A6, task "cancellation" |
| Q7 | Tồn kho gốc nằm ở đâu (DB hệ thống Nexacro hiện tại, ERP, WMS)? Có phải đồng bộ sang hệ thống khác không ("Inventory Quantity Synchronization" có nghĩa là đồng bộ với ERP?) | A8, có thể cần interface / batch |
| Q8 | Lịch sử tồn kho cần những trường gì (giá trị tiền, lý do, kho, lô…)? Có báo cáo nào đọc bảng này không? | Bảng `inventory_transaction` |
| Q9 | Trên PDA có cần **quét barcode** từng mặt hàng để đối chiếu trước khi xác nhận không? | Màn chi tiết |
| Q10 | Quy tắc đánh số phiếu? Múi giờ hiển thị? | Hiển thị |
| Q11 | Màn này làm trên app Android, trên web Nexacro, hay cả hai? Nếu cả hai: dùng chung API (cách B) hay qua gateway X-API (cách A) | Kiến trúc, xem `NEXACRO_XAPI_TO_JSON.md` |

---

## 6. Nếu làm trên Nexacro (để đối chiếu khi đọc code cũ)

| Android (demo) | Nexacro tương ứng (thường gặp) |
|---|---|
| `DisposalListActivity` + Spinner trạng thái | Form tra cứu: Combo trạng thái (`ds_cond`), Grid `ds_list`, `transaction("search", "svc::disposal/list.do", "ds_cond=ds_cond", "ds_list=ds_list")` |
| `DisposalDetailActivity` | Form chi tiết / popup: `ds_master` (header) + `ds_detail` (dòng) |
| Nút Xác nhận + `AlertDialog` | Button + `this.confirm("…")` → `transaction("confirm", "svc::disposal/confirm.do", "ds_master=ds_master", …)` |
| `version` gửi kèm | Thường so `UPD_DT` (ngày sửa cuối) trong `ds_master` với DB |
| Lỗi 409 / 422 + `details` | `ErrorCode < 0`, `ErrorMsg` trong `fn_callback` |
| `@Transactional` + khóa dòng | Service Java phía X-API (`@Transactional`, `SELECT … FOR UPDATE` trong mapper MyBatis) |

---

## 7. Chạy demo

**Backend:** chạy nhánh này (local hoặc Render). Dữ liệu mẫu tự tạo khi DB trống:

| Phiếu | Trạng thái | Nội dung | Kỳ vọng khi bấm Xác nhận |
|---|---|---|---|
| DSP-20260929-001 | Chờ xác nhận | Sữa TH ×5, Mì Hảo Hảo ×10 | Thành công: sữa 40 → 35, mì 85 → 75 |
| DSP-20260929-002 | Chờ xác nhận | Nước suối ×3 | Thành công |
| DSP-20260929-003 | Chờ xác nhận | Cà phê ×30 (khả dụng 20), Dầu ăn ×2 (tồn 0), Bánh quy ×5 | Nút bị tắt; 2 dòng tô đỏ; qua API → 422, không trừ gì |
| DSP-20260928-001 | Đã xác nhận | Sting ×4 | Không có nút |
| DSP-20260927-001 | Đã hủy | Bánh quy ×1 | Không có nút |

**App:** Build Variant `pollingDebug` → Run → login **`admin`** → **"Phiếu hủy hàng"**. Login `user` thì xem được nhưng không có nút Xác nhận.

**Kịch bản thử "dữ liệu cũ":** mở chi tiết DSP-…-002 trên app, rồi xác nhận phiếu đó bằng curl; quay lại app bấm Xác nhận → báo "đã được xác nhận trước đó" + nút Tải lại.

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/disposals?status=REQUESTED"
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/disposals/DSP-20260929-003
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}' \
  http://localhost:8080/api/disposals/DSP-20260929-002/confirm
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/inventory/8901234567890/transactions
```

Kết quả thật khi xác nhận DSP-…-003 (thiếu tồn):
```json
{"code":"INSUFFICIENT_QTY","message":"Không đủ số lượng khả dụng để hủy",
 "details":["8936036020151 (Cà phê G7 3in1 (hộp 18 gói)): cần hủy 30, khả dụng 20 (tồn 25, đã giữ chỗ 5)",
            "8935049510864 (Dầu ăn Neptune 1L): cần hủy 2, khả dụng 0 (tồn 0, đã giữ chỗ 0)"]}
```

---

## 8. Điểm nên học từ task này

| Chủ đề | Ở đâu trong code |
|---|---|
| Transaction: xác nhận + trừ tồn + ghi lịch sử thành công cùng lúc hoặc rollback hết | `DisposalService.confirm` (`@Transactional`) |
| Khóa bi quan (`PESSIMISTIC_WRITE`) và khóa lạc quan (`@Version`), khóa theo thứ tự để tránh deadlock | `InventoryItemRepository`, `DisposalRequest.version` |
| Kiểm tra hết rồi mới ghi; trả đủ danh sách lỗi 1 lần | `DisposalService.shortages` |
| Mã lỗi HTTP có nghĩa (404 / 409 / 422 / 403) + body lỗi thống nhất; app đọc `errorBody` | `DisposalController`, `model/ApiErrorDto` |
| App không tin chính nó: ẩn / tắt nút cho tiện, server vẫn kiểm tra lại | `DisposalDetailActivity.render` |
| Mất mạng khi đang gửi lệnh ghi: không biết server đã xử lý chưa → tải lại trạng thái, không gửi lại mù | `DisposalDetailActivity.confirm → onFailure` |
