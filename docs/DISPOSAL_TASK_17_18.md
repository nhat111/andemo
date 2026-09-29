# Task 17–18: Hủy hàng (폐기) theo luồng bán lẻ Hàn Quốc

> Nhánh: `claude/android-nexacro-migration`. Cập nhật: 2026-09-29.
> Bản Nexacro mobile "cũ" của cùng chức năng (để luyện đọc code và migrate): [nexacro-sample/disposal/README.md](../nexacro-sample/disposal/README.md).
> Chưa có spec, nên làm theo **luồng 폐기 thường gặp ở chuỗi bán lẻ Hàn Quốc** (cửa hàng tiện lợi CU / GS25 / 세븐일레븐, siêu thị). Khi có spec thật, đối chiếu lại các giả định ở mục 6 và câu hỏi ở mục 7.

---

## 1. Requirement nhận được

| # | Chức năng | Hạng mục | Nội dung |
|---|---|---|---|
| 17 | Add disposal item registration / inquiry / edit / cancellation functions (scrap items) | 1/ Confirm disposal items | Add Disposal Inquiry Screen · Add Disposal Detail View · Add Confirm Button · Disposal Status Validation |
| 18 | (như trên) | 2/ Update inventory items | Inventory Deduction Processing · Inventory Quantity Synchronization · Inventory Transaction History Update · Validation of Available Quantity |

Tiêu đề chức năng gồm cả **đăng ký / tra cứu / sửa / hủy**, nên demo làm **cả vòng đời phiếu**, không chỉ phần xác nhận.

---

## 2. Thuật ngữ

| Tiếng Hàn | Đọc | Nghĩa trong hệ thống |
|---|---|---|
| 폐기 | pye-gi | Hủy hàng (scrap / disposal) |
| 폐기전표 | pye-gi jeon-pyo | Phiếu hủy hàng (chứng từ) |
| 전표번호 | jeon-pyo beon-ho | Số chứng từ, ví dụ `S001-20260929-0001` (cửa hàng-ngày-số thứ tự) |
| 등록 / 수정 / 취소 | deung-rok / su-jeong / chwi-so | Đăng ký / sửa / hủy phiếu |
| 확정 / 확정취소 | hwak-jeong / hwak-jeong chwi-so | Xác nhận (trừ tồn) / hủy xác nhận (đảo lại) |
| 점원 / 점장 | jeom-won / jeom-jang | Nhân viên cửa hàng / cửa hàng trưởng |
| 영업일자 | yeong-eop il-ja | Ngày kinh doanh mà chứng từ thuộc về |
| 마감 (일마감) | ma-gam | Chốt sổ cuối ngày: số liệu đã gửi 본부 (trụ sở) / kế toán, không được sửa nữa |
| 현재고 / 가용재고 | hyeon-jae-go / ga-yong-jae-go | Tồn thực tế / tồn khả dụng (= tồn − đã giữ chỗ) |
| 수불 | su-bul | Sổ nhập–xuất–tồn: mọi biến động tồn đều ghi 1 dòng |
| 역분개 | yeok-bun-gae | Bút toán đảo: không xóa dòng cũ mà ghi thêm dòng ngược dấu |
| 원가 / 매가 | won-ga / mae-ga | Giá vốn / giá bán |
| 폐기사유 | pye-gi sa-yu | Lý do hủy: 유통기한 경과 (hết hạn), 파손 (vỡ), 변질 (biến chất), 리콜 (thu hồi), 품질불량 (lỗi chất lượng), 기타 (khác) |

---

## 3. Luồng nghiệp vụ

```
 점원 (nhân viên, PDA)                  점장 (cửa hàng trưởng)                Hệ thống
 ─────────────────────                  ──────────────────────                ─────────────────────────────────────
 Kiểm hàng hết hạn / hư hỏng
 Quét barcode từng món, chọn 사유
 Lưu → 폐기등록 (10)  ───────────────►  폐기조회: xem phiếu 등록
 (sửa / hủy phiếu của mình được)        폐기상세: SL hủy, tồn khả dụng, tiền
                                        Bấm 확정 ──────────────────────────► Kiểm tra: trạng thái 10, chưa 마감,
                                                                              version, đủ 가용재고
                                                                              Khóa tồn → trừ 현재고 → ghi 수불
                                                                              Phiếu → 확정 (20)
                                                                              (1 transaction: đủ hết hoặc không gì)
                                        Nhầm? 확정취소 (trước 마감) ────────► Cộng lại tồn, ghi 수불 ngược dấu
                                                                              Phiếu → 등록 (10) để sửa / xác nhận lại
 Cuối ngày ─────────────────────────────────────────────────────────────────► 일마감: chốt 영업일자, khóa mọi thay đổi
```

### Trạng thái phiếu

```
            수정 (sửa)
             ┌──┐
             ▼  │
  ──등록──► 10 등록 ────확정────► 20 확정
             │   ▲                  │
        취소 │   └──── 확정취소 ─────┘   (chỉ khi 영업일자 chưa 마감)
             ▼
          90 취소
```

| Thao tác | Trạng thái được phép | Ai | Điều kiện khác | Tồn kho |
|---|---|---|---|---|
| 등록 (đăng ký) | – | Mọi nhân viên | 영업일자 hôm nay chưa 마감; dữ liệu hợp lệ | Không đổi |
| 수정 (sửa) | 10 | Người đăng ký hoặc 점장 | Chưa 마감; đúng version | Không đổi |
| 취소 (hủy phiếu) | 10 | Người đăng ký hoặc 점장 | Chưa 마감; đúng version; **bắt buộc lý do** | Không đổi |
| 확정 (xác nhận) | 10 | 점장 | Chưa 마감; đúng version; **đủ 가용재고 mọi dòng** | Trừ, ghi 수불 `DISPOSAL` |
| 확정취소 (hủy xác nhận) | 20 | 점장 | Chưa 마감; đúng version; **bắt buộc lý do** | Cộng lại, ghi 수불 `DISPOSAL_CANCEL` |
| 일마감 (chốt sổ, demo) | – | 점장 | Không chốt ngày tương lai, không lùi | – |

---

## 4. Đối chiếu hạng mục với code

| Hạng mục | Cách làm | Code | Test |
|---|---|---|---|
| **17 – Inquiry Screen** | 폐기조회: lọc trạng thái (mặc định 등록), khoảng 영업일자; hiện số dòng, SL, tiền giá vốn | `GET /api/disposals?status=&from=&to=` · Android `DisposalListActivity` | `inquiryFiltersByStatusCodeAndBusinessDate` |
| **17 – Detail View** | 폐기상세: header (ngày, người, lịch sử thao tác), từng dòng (사유, tồn khả dụng, tiền), tô đỏ dòng thiếu; server trả luôn `actions` (nút nào được bấm) và `issues` (vì sao chưa xác nhận được) | `GET /api/disposals/{no}` · `DisposalDetailActivity` | `detailShowsAvailableQtyIssuesAndAllowedActionsPerRole` |
| **17 – Confirm Button** | Chỉ 점장 thấy; tắt nếu còn `issues`; hỏi lại (hiện số tiền bị trừ); chống bấm 2 lần | `POST /api/disposals/{no}/confirm` | `confirmDeductsStockAndWritesLedgerWithCost`, `onlyManagerConfirmsAndOnlyOnce` |
| **17 – Status Validation** | Mỗi thao tác kiểm tra trạng thái cho phép, 마감, version (dữ liệu cũ), quyền | `DisposalService.requireStatus / ensureNotClosed / requireVersion` | `cancelRequiresReasonAndBlocksLaterActions`, `closedBusinessDate…` |
| **18 – Deduction** | Trừ 현재고 từng dòng, cùng transaction với đổi trạng thái | `DisposalService.confirm → moveStock` | `confirmDeductsStockAndWritesLedgerWithCost` |
| **18 – Synchronization** | Khóa dòng tồn (`SELECT … FOR UPDATE`, theo thứ tự mã hàng để tránh deadlock) + `@Version` trên phiếu và tồn. Hủy xác nhận cộng lại đúng số đã trừ | `InventoryItemRepository.lockByItemCodes` | `twoManagersConfirmingAtTheSameTimeDeductOnlyOnce`, `cancelConfirmRestores…` |
| **18 – History Update** | 수불: loại, SL (±), tồn trước/sau, **tiền giá vốn**, 영업일자, số phiếu + dòng, người, thời điểm. Chỉ thêm; hủy xác nhận ghi dòng ngược dấu (역분개) | Bảng `inventory_transaction` · `GET /api/inventory/{code}/transactions` | `confirmDeducts…`, `cancelConfirmRestoresStockWithReversalEntry…` |
| **18 – Available Qty** | 가용재고 = 현재고 − giữ chỗ. Kiểm tra hết các dòng rồi mới trừ; 1 dòng thiếu → không trừ gì, báo đủ mọi dòng thiếu. Lúc đăng ký chỉ cảnh báo (tô đỏ), chặn ở bước 확정 | `DisposalService.shortages` → 422 | `insufficientAvailableQtyRejectsWholeSlip` |
| Đăng ký / sửa / hủy (tiêu đề chức năng) | Quét barcode trên PDA (camera hoặc scanner keyboard wedge + Enter), quét trùng thì +1, chạm dòng sửa SL / 사유, giữ lâu xóa. Kiểm tra mọi dòng 1 lần (trùng, không có hàng, SL ≤ 0, thiếu 사유) | `POST/PUT /api/disposals`, `POST …/cancel` · `DisposalEditActivity` | `registerCreatesNextSlipNumberWithPriceSnapshot`, `registerValidatesEveryLineAtOnce`, `ownerCanEditRegisteredSlipOthersCannot` |

Backend: package `backend/.../disposal/`. Test: `DisposalIntegrationTest` (13 test). Toàn bộ backend: 65/65.

### Dữ liệu

| Bảng | Cột chính |
|---|---|
| `disposal_request` (폐기전표) | `disposal_no`, `store_code`, `business_date`, `status`, `remark`, `registered_by/at`, `updated_by/at`, `confirmed_by/at`, `cancelled_by/at`, `cancel_reason`, `confirm_cancelled_by/at`, `confirm_cancel_reason`, `version` |
| `disposal_item` | `disposal_no`, `line_no`, `item_code`, `item_name`, `qty`, `reason_code`, `cost_price`, `sale_price` (chụp giá lúc đăng ký) |
| `inventory_item` (점포재고) | `item_code`, `item_name`, `store_code`, `on_hand_qty`, `allocated_qty`, `cost_price`, `sale_price`, `version` |
| `inventory_transaction` (수불) | `store_code`, `business_date`, `item_code`, `tx_type`, `qty_change`, `before_qty`, `after_qty`, `cost_amount`, `ref_no`, `ref_line_no`, `created_by/at` |
| `store_closing` (마감) | `store_code`, `last_closed_date`, `closed_by/at` |
| `document_sequence` | `sequence_key` (`DSP-S001-20260929`), `last_seq`: sinh 전표번호 không trùng (khóa dòng khi lấy số) |

### API

| Method | URL | Quyền |
|---|---|---|
| GET | `/api/disposals?status=10\|20\|90\|REGISTERED…&from=yyyy-MM-dd&to=` | Đăng nhập |
| GET | `/api/disposals/{no}` | Đăng nhập (`actions` tùy người xem) |
| POST | `/api/disposals` body `{remark, items:[{itemCode, qty, reasonCode}]}` | Đăng nhập |
| PUT | `/api/disposals/{no}` body `{version, remark, items}` | Người đăng ký / 점장 |
| POST | `/api/disposals/{no}/cancel` body `{version, reason}` | Người đăng ký / 점장 |
| POST | `/api/disposals/{no}/confirm` body `{version}` | 점장 |
| POST | `/api/disposals/{no}/cancel-confirm` body `{version, reason}` | 점장 |
| GET | `/api/disposals/reasons` | Đăng nhập |
| GET | `/api/inventory/{itemCode}` · `/api/inventory/{itemCode}/transactions` | Đăng nhập |
| GET / POST | `/api/store/closing` body `{closeDate}` | Xem: đăng nhập · Chốt: 점장 |

Lỗi: `{"code", "message", "details": [...]}`

| HTTP | code | Khi nào |
|---|---|---|
| 400 | `VALIDATION` | Dữ liệu sai (details liệt kê từng dòng), thiếu lý do hủy |
| 403 | `FORBIDDEN` | Không phải 점장 / không phải người đăng ký |
| 404 | `NOT_FOUND`, `ITEM_NOT_FOUND` | Không có phiếu / mặt hàng |
| 409 | `INVALID_STATUS` | Trạng thái không cho phép thao tác |
| 409 | `STALE_DATA` | Version cũ, hoặc người khác vừa thao tác cùng lúc |
| 409 | `CLOSED_PERIOD` | 영업일자 đã 마감 |
| 422 | `INSUFFICIENT_QTY` | Thiếu 가용재고 |

---

## 5. Chạy demo

Dữ liệu mẫu tự tạo khi DB trống. **Hôm qua đã 마감**, hôm nay chưa. Giá theo KRW.

| Phiếu | Trạng thái | Người | Nội dung | Thử được |
|---|---|---|---|---|
| `S001-<hôm nay>-0001` | 10 등록 | user | Kimbap ×3, Sữa ×2, Mì ×1 | Sửa (user), xác nhận (admin), rồi hủy xác nhận |
| `S001-<hôm nay>-0002` | 10 등록 | user2 | Nước suối ×3 | user không sửa được (không phải người đăng ký) |
| `S001-<hôm nay>-0003` | 10 등록 | user | Cà phê ×30 (khả dụng 20), Dầu ăn ×2 (tồn 0), Bánh quy ×5 | Nút xác nhận bị tắt, 2 dòng đỏ |
| `S001-<hôm qua>-0001` | 20 확정 | user | Sting ×4 | Ngày đã 마감: không hủy xác nhận được |
| `S001-<hôm qua>-0002` | 90 취소 | user2 | Bánh quy ×1 | Chỉ xem |

Barcode để thử quét / nhập tay trên màn đăng ký: `8801111222333` (kimbap), `8851993123456` (sữa), `8901234567890` (nước suối), `8936036020151` (cà phê).

**App:** Build Variant `pollingDebug` → Run → **"Phiếu hủy hàng"**.
- Login `user`: đăng ký phiếu mới (nút dưới cùng), sửa / hủy phiếu của mình.
- Login `admin`: xác nhận, hủy xác nhận.
- Scanner cứng của PDA: để ở chế độ keyboard wedge + gửi Enter; con trỏ ở ô barcode, quét là tự thêm.

**Kết quả thật** (chạy bằng Retrofit + model của app với backend):
```
1. 등록 S001-20260929-0004 status=10 cost=2050 actions edit=true confirm=false
2. 수정 lines=1 qty=3 by=user
3. user 확정 -> HTTP 403 Chỉ 점장 (ADMIN) được xác nhận phiếu hủy
4. 확정 status=20 by=admin · kimbap onHand=9
5. 확정취소 không lý do -> HTTP 400 VALIDATION
6. 확정취소 status=10 reason=Xác nhận nhầm · kimbap onHand=12
7. 취소 status=90 reason=Không cần hủy nữa
8. 확정취소 ngày đã 마감 S001-20260928-0001 -> HTTP 409 영업일자 2026-09-28 đã chốt sổ (마감), không thay đổi được
```

curl:
```bash
tok() { curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"$1\",\"password\":\"123456\"}" | sed 's/.*"token":"\([^"]*\)".*/\1/'; }
U=$(tok user); A=$(tok admin)
curl -s -H "Authorization: Bearer $U" "http://localhost:8080/api/disposals?status=10"
curl -s -X POST -H "Authorization: Bearer $U" -H 'Content-Type: application/json' \
  -d '{"remark":"Hủy cuối ca","items":[{"itemCode":"8801111222333","qty":2,"reasonCode":"EXPIRED"}]}' \
  http://localhost:8080/api/disposals
curl -s -X POST -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d '{}' \
  http://localhost:8080/api/disposals/S001-$(TZ=Asia/Seoul date +%Y%m%d)-0004/confirm
curl -s -H "Authorization: Bearer $U" http://localhost:8080/api/inventory/8801111222333/transactions
```

---

## 6. Giả định (theo thông lệ bán lẻ Hàn Quốc, cần xác nhận)

| # | Giả định | Vì sao chọn |
|---|---|---|
| A1 | 2 bước: 점원 **등록** → 점장 **확정**; chỉ 확정 mới trừ tồn | Tách người làm và người duyệt, phổ biến ở cửa hàng |
| A2 | Tất cả hoặc không: 1 dòng thiếu 가용재고 thì cả phiếu không xác nhận được | Tránh phiếu "xác nhận một nửa" khó đối soát |
| A3 | Lúc đăng ký chỉ cảnh báo thiếu tồn, không chặn | Nhân viên cầm hàng thật trong tay; tồn hệ thống có thể lệch, 점장 xử lý |
| A4 | Hủy xác nhận được **tới khi 마감**; sau 마감 phải làm nghiệp vụ điều chỉnh tồn (재고조정) riêng | Sau 마감 số liệu đã gửi 본부 / kế toán |
| A5 | 수불 chỉ thêm, đảo bằng dòng ngược dấu (역분개) | Truy vết được, khớp kế toán |
| A6 | Tiền hủy tính theo **giá vốn chụp lúc đăng ký**; lưu cả giá bán | Báo cáo 폐기손실 (lỗ do hủy) theo giá vốn; đổi giá sau không ảnh hưởng phiếu cũ |
| A7 | 1 phiếu không có 2 dòng cùng mặt hàng (app tự +1 khi quét trùng) | Dễ đối soát; mỗi dòng 1 사유 |
| A8 | 1 cửa hàng `S001`, 영업일자 đổi lúc 0h giờ Hàn; không quản lý lô / hạn dùng / vị trí | Rút gọn demo |
| A9 | Tồn nằm trong DB này; chưa gửi 본부 / ERP | Chưa có thông tin interface |

---

## 7. Câu hỏi cho BA / khách hàng

| # | Câu hỏi | Ảnh hưởng |
|---|---|---|
| Q1 | Các cột `1, 3` / `1, 3, 4.6` trong task list nghĩa là gì? | Kế hoạch |
| Q2 | Duyệt 1 cấp (점장) hay nhiều cấp (점장 → 본부 / SV)? 본부 có duyệt phiếu hủy giá trị lớn không? | A1, thêm trạng thái |
| Q3 | Hủy xác nhận được tới khi nào (trong ngày, trước 마감, trước 월마감)? | A4 |
| Q4 | Có quản lý theo lô / hạn dùng (유통기한) / vị trí không? Hàng tươi (도시락, 삼각김밥) có tự tạo phiếu hủy theo 판매기한 không? | A8, cấu trúc bảng |
| Q5 | 가용재고 trừ những gì (반품 대기, 점간이동, 발주…)? Có cho tồn âm không? | A2, A3 |
| Q6 | Có 폐기지원 (본부 hỗ trợ chi phí hủy hàng tươi) không? Nếu có, tính theo tỉ lệ nào, nằm ở màn nào? | Thêm cột tiền |
| Q7 | Phiếu hủy / 수불 có phải gửi sang ERP / 본부 không, gửi lúc 확정 hay lúc 마감? | A9, interface |
| Q8 | 전표번호 theo quy tắc nào? | Sinh số |
| Q9 | Màn này làm trên app PDA, web Nexacro, hay cả hai? Web Nexacro dùng chung API này (cách B) hay qua gateway (cách A)? | Kiến trúc, `NEXACRO_XAPI_TO_JSON.md` |
| Q10 | Có cần in / xuất phiếu hủy, báo cáo 폐기현황 theo ngày / tháng / 사유 không? | Màn báo cáo |

---

## 8. Nếu hệ thống cũ làm bằng Nexacro (để đọc code cũ)

| Demo (Android + JSON) | Nexacro thường gặp |
|---|---|
| `DisposalListActivity` | Form 폐기조회: Calendar 영업일자 from–to, Combo trạng thái (`ds_cond`), Grid `ds_list` · `transaction("search", "svc::disposal/selectList.do", "ds_cond=ds_cond", "ds_list=ds_list")` |
| `DisposalDetailActivity` | Form / popup chi tiết: `ds_master` (header) + `ds_detail` (dòng) |
| `DisposalEditActivity` | Grid nhập liệu `ds_detail` (thêm dòng, rowtype insert/update/delete) · lưu bằng `"ds_master=ds_master ds_detail=ds_detail:U"` |
| Nút theo `actions` | `gfn_` kiểm tra trạng thái / quyền rồi `set_enable` từng Button |
| `version` | Thường so `UPD_DT` (thời điểm sửa cuối) |
| Lỗi 400 / 409 / 422 + `details` | `ErrorCode < 0`, `ErrorMsg` trong `fn_callback` |
| `@Transactional` + khóa dòng | Service Java phía X-API, `SELECT … FOR UPDATE` trong mapper MyBatis |

---

## 9. Điểm nên học từ task này

| Chủ đề | Ở đâu |
|---|---|
| Máy trạng thái: mỗi thao tác kiểm tra trạng thái cho phép | `DisposalService.requireStatus`, sơ đồ mục 3 |
| Transaction: đổi trạng thái + tồn + 수불 cùng thành công hoặc rollback | `DisposalService.confirm / cancelConfirm` |
| Khóa bi quan (tồn kho, số chứng từ) và khóa lạc quan (`@Version`) | Repository, entity |
| Đảo chứng từ bằng bút toán ngược, không xóa lịch sử | `moveStock` với `DISPOSAL_CANCEL` |
| Chốt kỳ (마감) chặn mọi thay đổi | `ensureNotClosed` |
| Server quyết định quyền, app chỉ hiển thị theo `actions` | `toDetail`, `DisposalDetailActivity.render` |
| Kiểm tra hết rồi báo 1 lần (validation, thiếu tồn) | `validateLines`, `shortages` |
| PDA: quét barcode bằng keyboard wedge (Enter), quét trùng +1 | `DisposalEditActivity.addScanned` |
| Mất mạng khi đang ghi: tải lại trạng thái, không gửi lại mù | `DisposalDetailActivity.run → onFailure` |
