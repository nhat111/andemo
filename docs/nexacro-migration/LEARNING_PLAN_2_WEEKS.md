# Lộ trình 2 tuần: đọc hiểu code để dùng được Android

> 📚 [Mục lục tài liệu](README.md) · Học xong → làm 1 màn: [công thức](HOW_TO_CODE_A_SCREEN.md)

> Dành cho: có **2 tuần** làm quen trước khi vào dự án migrate Nexacro 17 mobile → Android (Java).
> Cách học: **đọc code có sẵn + chạy + sửa nhỏ**, không dựng lại từ đầu. Mỗi ngày ~2 giờ, 10 ngày làm việc.
> Muốn học sâu hơn sau này: [LEARNING_PLAN.md](LEARNING_PLAN.md) (6 tuần).

---

## 1. Học trên cái gì

Chức năng **hủy hàng (폐기)** có đủ 3 bản trong repo, cùng dữ liệu, cùng quy tắc:

| Bản | Ở đâu | Vai trò khi học |
|---|---|---|
| Nexacro 17 mobile "cũ" | `nexacro-sample/disposal/` (4 form + `lib/common.xjs`) | Giống code bro sẽ gặp ở dự án |
| Server | `backend/.../disposal/` (API JSON) + `backend/.../nexacro/LegacyDisposalController.java` (`*.do` XML cũ) | Cả 2 dùng chung `DisposalService` |
| Android (đã migrate đầy đủ) | `android/.../disposal/`, `api/DisposalApi.java`, `model/Disposal*.java` | Mẫu để đọc, sửa, dùng lại cấu trúc |

Bảng đối chiếu 13 quy tắc giữa 2 bản: `nexacro-sample/disposal/README.md` mục 7. Tra khái niệm: [NEXACRO_TO_ANDROID.md](NEXACRO_TO_ANDROID.md).

**Quy ước mỗi ngày:** **Đọc** → **Chạy / thử** → **Sửa nhỏ** (để chắc là hiểu; sửa xong có thể trả lại như cũ) → **Tự kiểm tra**.

---

## 2. Tuần 1: Nhìn toàn cảnh và đọc từng lớp

### Ngày 1: Chạy toàn bộ, hiểu nghiệp vụ

- **Đọc:** [../task-17-18-disposal/README.md](../task-17-18-disposal/README.md) mục 2 (thuật ngữ), mục 3 (luồng, trạng thái 10 / 20 / 90).
- **Chạy:** backend + app (Build Variant `pollingDebug`, `apiBaseUrl=http://10.0.2.2:8080/`), theo [../LOCAL_SETUP_GUIDE.md](../LOCAL_SETUP_GUIDE.md).
- **Thử trên app, đi hết luồng:**
  1. Login `user` → "Phiếu hủy hàng" → "+ Đăng ký" → gõ barcode `8801111222333` + Enter 2 lần (thấy SL = 2) → Lưu.
  2. Logout, login `admin` → mở phiếu vừa tạo → Xác nhận → thấy "Đã xác nhận".
  3. Hủy xác nhận (nhập lý do) → phiếu về 등록.
  4. Mở phiếu `…-0003` (thiếu tồn): dòng đỏ, nút Xác nhận bị tắt.
- **Tự kiểm tra:** kể lại được luồng 등록 → 확정 → 확정취소, và tồn kho đổi ở bước nào.

### Ngày 2: Đọc bản Nexacro

- **Đọc:** [nexacro-sample/disposal/README.md](../../nexacro-sample/disposal/README.md) mục 1–4 (cấu trúc, **luồng đầy đủ**, transaction); `lib/common.xjs`; `form/frm_login.xfdl`; `form/frm_disposal_list.xfdl`.
- **Chạy:** README mục 5: login + tra cứu bằng curl (có cookie và không có cookie).
- **Sửa nhỏ:** tự viết lệnh curl gọi `disposal/selectDetail.do` cho phiếu `S001-<hôm nay>-0001`.
- **Tự kiểm tra:** `gfn_transaction` → `gfn_callback` → hàm callback của form chạy thế nào? `-99` dẫn về đâu? `ds_detail:U` gửi những dòng nào?

### Ngày 3: Khung project Android và vòng đời màn hình

- **Đọc:** `android/app/build.gradle` (flavors, `apiBaseUrl`), `AndroidManifest.xml` (các Activity), `MainActivity.java`, `LoginActivity.java`, `res/layout/activity_main.xml`.
- **Học:** Activity = 1 form; vòng đời `onCreate` → `onResume` → `onPause` → `onDestroy`; xoay màn hình thì Activity bị tạo lại.
- **Sửa nhỏ:** thêm `Log.d("LIFE", "onCreate")`, `onResume`, `onPause` vào `disposal/DisposalListActivity`; chạy, xoay màn hình, bấm Home rồi quay lại, xem Logcat (lọc `tag:LIFE`).
- **Tự kiểm tra:** `this.go("frm::…")` + `gv_disposalNo` của Nexacro tương ứng gì trên Android? (Tìm `putExtra` trong `DisposalListActivity`.)

### Ngày 4: Gọi server (kiểu của khách trước, Retrofit sau)

Code của khách gọi API bằng **thread + `HttpURLConnection`**, không Retrofit → học bản đó trước. Đọc kèm [PLAIN_JAVA_STYLE.md](PLAIN_JAVA_STYLE.md).

- **Đọc (chính):** `plain/HttpTask.java` (`call`, `readAll`, `errorMessage`), `plain/JsonRows.java`.
- **Đọc (so sánh, lướt):** `api/ApiClient.java`, `api/AuthInterceptor.java`, `api/TokenAuthenticator.java`, `api/DisposalApi.java`, `model/DisposalSummaryDto.java`.
- **So với:** `gfn_transaction` / `gfn_callback` trong `common.xjs`.
- **Học:** vì sao phải chạy luồng nền (`ExecutorService`) rồi `Handler` về luồng giao diện; `getErrorStream()` cho 4xx; timeout; UTF-8; `org.json` (`JSONObject`, `JSONArray`, `optString`). Retrofit làm đúng các việc đó, chỉ là tự động.
- **Chạy / thử:** mở **"Phiếu hủy hàng (Java thuần)"**, Logcat lọc `tag:HttpTask` (thấy URL + mã HTTP). Tắt backend rồi bấm "Tải lại": app báo lỗi (`onError` code 0), không crash.
- **Sửa nhỏ:** trong `PlainDisposalListActivity.search()`, thêm `Log.d` in số phiếu nhận được.
- **Tự kiểm tra:** `errorCode < 0` trong `fn_callback` tương ứng chỗ nào trong `HttpTask`? Nếu gọi `setText` ngay trong `EXECUTOR.execute(...)` thì sao?

### Ngày 5: Màn danh sách (Grid → ListView / RecyclerView)

- **Đọc cặp (kiểu khách):** `frm_disposal_list.xfdl` ↔ `plain/PlainDisposalListActivity.java` + `PlainDisposalListAdapter.java` + `activity_plain_disposal_list.xml` + `item_plain_disposal.xml`.
- **Đọc cặp (Retrofit):** `disposal/DisposalListActivity.java` + `DisposalListAdapter.java` + `res/layout/activity_disposal_list.xml` + `item_disposal.xml`.
- **Học kiểu khách:** view cất trong `HashMap<String, View>`, 1 `onClick(View)` chung phân nhánh bằng `if / else` theo `R.id` (AGP 8 không cho `switch`); `ListView` + adapter tự viết (`BaseAdapter`: `getCount` / `getView`, `convertView` + ViewHolder) với `List<HashMap>` ≈ Grid + Dataset. Xem [PLAIN_JAVA_STYLE.md mục 3](PLAIN_JAVA_STYLE.md). Đọc `PlainDisposalLineAdapter` (viết tay đủ bước) trước, rồi `HashMapListAdapter` + `PlainDisposalListAdapter` (khung, còn 2 bước). Chạy `devcheck\run.bat`, rồi thử đổi 1 id trong `bind` thành id không có trong `item_plain_disposal.xml` để thấy `ProjectCheck` báo lỗi ([WORK_WITHOUT_BUILD.md](WORK_WITHOUT_BUILD.md)).
- **Học:** RecyclerView = Grid; Adapter = Band body (gán cột vào ô); `notifyDataSetChanged()` = Grid vẽ lại khi Dataset đổi; Spinner = Combo.
- **Sửa nhỏ (kiểu khách):** trong `PlainDisposalListAdapter.getView`, hiện thêm **tổng giá bán** (`r.get("totalSaleAmount")`, định dạng bằng `JsonRows.won`); phiếu có giá vốn ≥ 100.000원 thì tô đỏ `txtSummary`. Cuộn qua lại để chắc dòng khác không bị dính màu.
- **Sửa nhỏ (Retrofit, nếu còn giờ):** làm tương tự với `getTotalSaleAmount()` trong `DisposalListAdapter`.
- **Tự kiểm tra:** vì sao tô màu trong `getView` / `onBindViewHolder` phải gán cả 2 nhánh? `convertView == null` tương ứng hàm nào của RecyclerView? Quy tắc R1 (từ ngày ≤ đến ngày) nằm ở đâu ở 2 bản?

---

## 3. Tuần 2: Màn nghiệp vụ, server, tổng kết

### Ngày 6: Màn chi tiết và nút theo quyền / trạng thái

- **Đọc cặp:** `frm_disposal_detail.xfdl` (`fn_setButtons`, `fn_checkStock`, `btn_confirm_onclick`) ↔ `plain/PlainDisposalDetailActivity.java` (`render`, `askConfirm`, `askReason`, `send`, `onStockClick`) + `PlainDisposalLineAdapter.java` (nút trong dòng) ↔ `disposal/DisposalDetailActivity.java` (`render`, `askConfirm`, `askConfirmBig`, `run`, `showError`) + `DisposalLineAdapter.java`.
- **Đọc thêm phía server:** `DisposalService.toDetail` (tạo `actions`, `issues`).
- **Học:** bản Nexacro tự quyết định nút trong script; bản Android hiện nút theo `actions` server trả → quy tắc nằm 1 chỗ.
- **Sửa nhỏ:** đổi `BIG_AMOUNT` trong `rules/DisposalRules.java` từ `100_000` thành `5_000`. Chạy `devcheck\run.bat`: test `R10 99.999 không hỏi lần 2` phải FAIL, mã thoát 1 (test bắt được thay đổi). Có máy để chạy app thì xác nhận phiếu `…-0001` để thấy hộp thoại hỏi lần 2. Trả lại như cũ, chạy lại thấy PASS. Cách test bằng `main`: [WORK_WITHOUT_BUILD.md](WORK_WITHOUT_BUILD.md) mục 4–6.
- **Tự kiểm tra:** R9 (chỉ xác nhận phiếu hôm nay) nằm ở đâu ở mỗi bản? Vì sao đưa về server?

### Ngày 7: Màn đăng ký / sửa, quét barcode

- **Đọc cặp:** `frm_disposal_reg.xfdl` (`fn_scan`, `fn_addItem`, `fn_validate`, `ds_detail:U`) ↔ `disposal/DisposalEditActivity.java` (`addScanned`, `addLine`, `editLine`, `removeLine`, `save`) + `EditLine.java`, `EditLineAdapter.java`.
- **Học:** scanner PDA ở chế độ gõ phím (keyboard wedge) gõ barcode + Enter vào ô đang focus; `setOnEditorActionListener` bắt Enter; `AlertDialog` thay Div popup.
- **Sửa nhỏ:** cho Spinner "Lý do mặc định" chọn sẵn **파손 (vỡ)** thay vì mục đầu tiên.
- **Tự kiểm tra:** Nexacro gửi `ds_detail:U` (chỉ dòng đổi); Android gửi gì khi sửa phiếu? (Xem `DisposalSaveRequest` và `PUT /api/disposals/{no}`.)

### Ngày 8: Server: nghiệp vụ, transaction, lỗi

- **Đọc:** `backend/.../disposal/DisposalService.java` (`register`, `confirm`, `cancelConfirm`, `validateLines`, `validRemark`, `requireToday`), `DisposalController.java` (mã lỗi HTTP), `InventoryItemRepository.lockByItemCodes`.
- **Học:** `@Transactional` (xác nhận + trừ tồn + ghi 수불 cùng thành công hoặc rollback), khóa dòng, `@Version` (2 người sửa cùng lúc).
- **Chạy:** `DisposalIntegrationTest` (IntelliJ: chuột phải → Run).
- **Sửa nhỏ:** tạm đổi `MAX_REMARK_LENGTH` thành 50, chạy lại test → thấy `r5_remarkLongerThan100IsRejected` đỏ. Trả lại như cũ.
- **Tự kiểm tra:** 400 / 403 / 409 / 422 trả khi nào? App hiện thông báo lỗi server ở hàm nào?

### Ngày 9: Server X-API cũ và chuyển đổi XML ↔ JSON

- **Đọc:** [NEXACRO_XAPI_TO_JSON.md](NEXACRO_XAPI_TO_JSON.md) mục 1–3; `backend/.../nexacro/LegacyDisposalController.java` (`call`, `save` với rowtype, `errorCode`).
- **Chạy:** màn "Demo X-API → JSON" trên app (4 nút); curl `disposal/confirm.do` với phiếu hôm qua → nhận ErrorCode âm.
- **Học:** vì sao dự án chọn API JSON + JWT (cách B) thay vì gọi thẳng `*.do` + cookie; nếu buộc phải gọi `*.do` thì cần `CookieJar`.
- **Tự kiểm tra:** 1 Dataset XML trở thành gì trong JSON? `-99`, `-4`, `-5`, `-6` nghĩa là gì?

### Ngày 10: Tổng kết + 1 thay đổi xuyên suốt

- **Đọc lại:** `nexacro-sample/disposal/README.md` mục 7 (13 quy tắc, 5 quy tắc chỉ có ở client) và [MIGRATION_PLAN.md](MIGRATION_PLAN.md) mục A6 (quy trình migrate 1 màn + Definition of Done).
- **Làm (thay đổi đi qua cả server, bản cũ và app):** thêm lý do hủy mới **고온변질 (hư do nhiệt)**:
  1. `DisposalReason.java`: thêm `HEAT("고온변질", "Hư do nhiệt độ")`.
  2. `LegacyDisposalController`: thêm mã cũ `"06"` vào `REASON_BY_CODE`.
  3. Sửa test đang đếm 6 lý do (`reasonsAndItemLookupForRegistrationScreen`, `loginThenInquiry…` trong `LegacyDisposalXapiTest`) thành 7.
  4. Chạy test backend; chạy app: màn đăng ký tự có lý do mới (app lấy danh sách từ server, không phải sửa app).
- **Làm thêm (quy tắc chỉ ở app, test bằng main):** thêm `DisposalRules.validQty(long)` (SL hủy > 0) + 2 dòng `check(...)` trong `devcheck/DisposalRulesCheck.java`, chạy `devcheck\run.bat`, rồi gọi hàm đó trong `DisposalEditActivity` trước khi thêm dòng. Làm theo [WORK_WITHOUT_BUILD.md](WORK_WITHOUT_BUILD.md) mục 6.
- **Tập dượt công thức làm việc:** đi qua [HOW_TO_CODE_A_SCREEN.md](HOW_TO_CODE_A_SCREEN.md) bước 1–6 với form mẫu `nexacro-sample/frm_product_search.xfdl` (điền phiếu phân tích, bảng API, copy khung code, chạy `devcheck\run.bat`). Đây là cách sẽ làm hằng ngày ở dự án.
- **Tự kiểm tra:** giải thích được với team: 1 màn Nexacro được chuyển sang Android theo những bước nào, quy tắc nào đưa về server.

---

## 4. Bảng tra nhanh Nexacro 17 → Android (dùng hằng ngày)

| Nexacro 17 | Android trong repo | Ví dụ |
|---|---|---|
| Form `.xfdl` | Activity + layout XML | `frm_disposal_list` → `DisposalListActivity` + `activity_disposal_list.xml` |
| `form_onload` | `onCreate` | |
| `this.go("frm::…")` + `gv_*` | `startActivity(intent.putExtra(…))` | `DisposalListActivity` → `DisposalDetailActivity` |
| Dataset (nhiều dòng) | `List<Model>` | `ds_list` → `List<DisposalSummaryDto>` |
| Grid | RecyclerView + Adapter | `DisposalListAdapter` |
| Combo (`innerdataset`) | Spinner + `ArrayAdapter` | Spinner trạng thái, lý do |
| Edit / `onkeyup` Enter | EditText / `setOnEditorActionListener` | ô barcode |
| Div popup | `AlertDialog` | nhập lý do hủy |
| `transaction` + `fn_callback` | Retrofit `enqueue` + `Callback` | `DisposalApi` |
| `ErrorCode < 0`, `ErrorMsg` | HTTP 4xx + `ApiErrorDto` | `showError` |
| `gfn_alert` / `gfn_confirm` | `Toast` / `AlertDialog` | |
| `set_visible` / `set_enable` theo quyền | `setVisibility` / `setEnabled` theo `actions` server | `DisposalDetailActivity.render` |
| session (`JSESSIONID`) | JWT (`AuthInterceptor`, `TokenAuthenticator`) | |
| `ds:U` (dòng thay đổi) | Gửi cả danh sách dòng | `DisposalSaveRequest` |
| Cell `cssclass="expr:…"` | `setTextColor` trong `onBindViewHolder` | dòng thiếu tồn tô đỏ |

---

## 5. Mẹo đọc code nhanh trong Android Studio

| Việc | Phím / cách làm |
|---|---|
| Nhảy tới định nghĩa | Ctrl + Click (macOS: Cmd + Click) |
| Ai gọi hàm này | Chuột phải → Find Usages (Alt + F7) |
| Tìm file theo tên | Shift 2 lần |
| Tìm chữ trong project | Ctrl + Shift + F |
| Dừng để xem giá trị | Click lề trái đặt breakpoint → Run bằng nút Debug (con bọ) |
| Xem log app | Logcat, lọc `package:mine`; lỗi: `package:mine level:error` |
| Xem request / response | Logcat lọc `okhttp` (bản debug in cả body) |

---

## 6. Theo dõi

| Ngày | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|---|---|---|---|
| Xong | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
