# Lab: học Android qua việc migrate màn hình Nexacro

> 📚 [Mục lục tài liệu](README.md) · Lộ trình: [2 tuần](LEARNING_PLAN_2_WEEKS.md) · Làm 1 màn: [công thức](HOW_TO_CODE_A_SCREEN.md)

> Nhánh: `claude/android-nexacro-migration` (tách từ `claude/pda-finder-fcm`, giữ nguyên toàn bộ phần PDA Finder để tham khảo).
> Đi kèm: [NEXACRO_TO_ANDROID.md](NEXACRO_TO_ANDROID.md) (bảng đối chiếu khái niệm), [ANDROID_LEARNING_PLAN.md](../ANDROID_LEARNING_PLAN.md) (plan học 14 buổi). Làm bài theo đúng các bước của [HOW_TO_CODE_A_SCREEN.md](HOW_TO_CODE_A_SCREEN.md).
> Cập nhật: 2026-09-29

---

## 1. Cách dùng nhánh này

1. Đọc **bài mẫu** (mục 3): 1 form Nexacro và bản Android tương ứng, đánh dấu từng phần bằng chữ (A)…(L) ở cả 2 bên.
2. Làm lần lượt **bài tập** (mục 4). Mỗi bài luyện 1 khái niệm Nexacro → Android, có gợi ý và tiêu chí "xong khi".
3. Làm xong bài nào thì commit lên nhánh này, nhờ mình review.
4. Khi có mã nguồn Nexacro thật của dự án: làm theo quy trình ở mục 5.

| Thư mục / file | Nội dung |
|---|---|
| `nexacro-sample/frm_product_search.xfdl` | Form Nexacro mẫu (rút gọn): tìm sản phẩm + Grid |
| `nexacro-sample/ProductSearchXapiController.java.txt` | Server kiểu X-API mẫu, so sánh với API JSON |
| `backend/.../controller/ProductController.java` → `search` | API JSON `GET /api/products?keyword=` thay cho `product/search.do` |
| `android/.../migration/ProductSearchActivity.java` | Form → Activity |
| `android/.../migration/ProductAdapter.java` | Grid → RecyclerView.Adapter |
| `android/app/src/main/res/layout/activity_product_search.xml` | Layout của form |
| `android/app/src/main/res/layout/item_product.xml` | 1 dòng của Grid |
| `backend/src/test/.../ProductSearchIntegrationTest.java` | Test API tra cứu |

Bài tập mới nên đặt code trong package `com.example.andemo.migration` và file `nexacro-sample/`, để tách khỏi code PDA Finder.

---

## 2. Chạy bài mẫu

API `GET /api/products` chỉ có trên nhánh này. Chọn 1 trong 2 cách:

**Backend trên laptop (khuyên dùng khi học):**
1. Checkout `claude/android-nexacro-migration`, chạy backend (IntelliJ: Run `AndemoApplication`, hoặc `mvn spring-boot:run` trong thư mục `backend`).
2. `android/gradle.properties`: `apiBaseUrl=http://10.0.2.2:8080/` (máy ảo) → Sync.

**Render:** đổi Branch của service sang `claude/android-nexacro-migration`. Nhánh này có đủ mọi thứ của nhánh fcm, nên PDA Finder vẫn chạy bình thường.

Rồi: Build Variant `pollingDebug` (không cần Firebase) hoặc `fcmDebug` → Run → login `user` → bấm **"Tra cứu sản phẩm (bài mẫu Nexacro)"**.

Mong đợi: mở màn hình là có 7 sản phẩm; gõ `sữa` → 1 sản phẩm; "Dầu ăn Neptune" tồn 0 hiện chữ đỏ; bấm 1 dòng → màn chi tiết có ảnh.

Kiểm tra API bằng trình duyệt / curl:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"user","password":"123456"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl -G -H "Authorization: Bearer $TOKEN" --data-urlencode "keyword=sữa" http://localhost:8080/api/products
```

---

## 3. Bài mẫu: đối chiếu từng phần

| # | Nexacro (`frm_product_search.xfdl`) | Android | Ghi chú |
|---|---|---|---|
| A | `Static` + `Edit edt_keyword` + `Button btn_search` | `EditText edtKeyword` + `Button btnSearch` trong `activity_product_search.xml` | Toạ độ `left/top/width` → `LinearLayout` tự xếp, không đặt toạ độ tuyệt đối |
| B | `Static sta_count` | `TextView tvCount` + `ProgressBar progress` | Android nên hiện trạng thái đang tải |
| C | `Grid grd_list binddataset="ds_list"` | `RecyclerView rvProducts` + `ProductAdapter` + `item_product.xml` | 3 cột ngang → dạng thẻ cho màn PDA hẹp |
| C | `Cell cssclass="expr:stockQuantity == 0 ? 'cell_short' : ''"` (class trong `.xcss`) | `onBindViewHolder`: `setTextColor(qty == 0 ? RED : DKGRAY)` | Luôn gán cả 2 nhánh vì ViewHolder bị tái sử dụng |
| D | `Dataset ds_search` (1 dòng) | Tham số `@Query("keyword")` trong `ProductService.search` | |
| E | `Dataset ds_list` (nhiều dòng) | `List<ProductDto>` (`model/ProductDto.java`) | Tên field Java = tên cột = tên key JSON |
| F | `BindItem edt_keyword ↔ ds_search.keyword` | `edtKeyword.getText().toString()` lúc bấm Tìm | Không có bind tự động |
| G | `form_onload` → `fn_search()` | `onCreate` → `search()` | |
| H | `btn_search_onclick` | `btnSearch.setOnClickListener(...)` | |
| I | `edt_keyword_onkeydown` (keycode 13) | `setOnEditorActionListener` + `imeOptions="actionSearch"` | Bàn phím ảo hiện nút "Tìm" |
| J | `transaction("search", "svc::product/search.do", ...)` | `api.search(keyword).enqueue(...)` | `svc::` → `apiBaseUrl` |
| K | `fn_callback(svcID, errorCode, errorMsg)` | `onResponse` (có trả lời, kể cả lỗi HTTP) / `onFailure` (mất mạng) | Kiểm tra `isFinishing()` trước khi đụng view |
| L | `grd_list_oncellclick` → `gv_barcode` + `go("frm::...")` | `ProductAdapter.OnItemClick` → `Intent.putExtra` + `startActivity` | Truyền dữ liệu qua Intent, không qua biến toàn cục |

Phía server: so sánh `nexacro-sample/ProductSearchXapiController.java.txt` với hàm `search` trong `ProductController.java`. Phần đọc/ghi Dataset biến mất, phần nghiệp vụ giữ nguyên.

---

## 4. Bài tập

Làm theo thứ tự; mỗi bài dựa trên bài trước. Cột "Buổi học" trỏ tới [ANDROID_LEARNING_PLAN.md](../ANDROID_LEARNING_PLAN.md).

### Bài 1: Làm quen bài mẫu
- **Nexacro:** đọc hiểu Form, Grid, transaction.
- **Buổi học:** 1–4.
- **Làm:**
  1. Chạy bài mẫu, đặt breakpoint trong `onResponse`, xem `response.body()`.
  2. Hiện thêm `description` dưới tên sản phẩm (sửa `item_product.xml` + `onBindViewHolder`).
  3. Tồn kho < 50 (nhưng > 0) hiện màu cam.
- **Xong khi:** 3 màu tồn kho đúng; cuộn lên xuống không bị sai màu.

### Bài 2: Thêm điều kiện tìm (CheckBox)
- **Nexacro:** thêm cột vào `ds_search`, thêm `CheckBox` bind vào cột đó.
- **Buổi học:** 3, 4.
- **Làm:** CheckBox "Chỉ còn hàng". Backend: thêm `@RequestParam(required = false) Boolean inStock`. Android: `@Query("inStock") Boolean inStock`. Tick/bỏ tick thì tìm lại ngay.
- **Gợi ý:** `checkBox.setOnCheckedChangeListener((b, checked) -> search())`. Retrofit bỏ qua `@Query` có giá trị `null`.
- **Xong khi:** tick → "Dầu ăn Neptune" biến mất. Thêm 1 test backend cho `inStock=true`.

### Bài 3: Combo mã chung (Spinner)
- **Nexacro:** `Combo` với `innerdataset` từ `gds_code`, `codecolumn` / `datacolumn`.
- **Buổi học:** 3, 4.
- **Làm:** thêm `category` cho sản phẩm (ví dụ `DRINK`, `FOOD`, `DAIRY`). API mới `GET /api/codes/product-category` trả `[{"code":"DRINK","name":"Đồ uống"}, ...]`. Android: `Spinner` "Tất cả / Đồ uống / Thực phẩm / Sữa", chọn thì lọc.
- **Gợi ý:** model `CodeItem` có `toString()` trả về `name` để `ArrayAdapter<CodeItem>` hiển thị tên; khi gửi lấy `getCode()`. Load danh sách mã 1 lần trong `onCreate`.
- **Xong khi:** chọn "Sữa" chỉ còn sản phẩm sữa; xoay màn hình không crash.

### Bài 4: Popup chọn và trả kết quả
- **Nexacro:** `showModal` mở popup, popup gọi `this.close(ret)`, form cha nhận trong callback.
- **Buổi học:** 2.
- **Làm:** màn mới `StockInActivity` (nhập kho) có ô "Sản phẩm" + nút "Chọn…". Nút mở `ProductSearchActivity` ở **chế độ chọn**: bấm 1 dòng thì trả barcode + tên về, không mở màn chi tiết.
- **Gợi ý:** `NEXACRO_TO_ANDROID.md` mục 4.5. Phân biệt chế độ bằng Intent extra, ví dụ `EXTRA_PICK_MODE = true`; ở chế độ chọn: `setResult(RESULT_OK, data)` + `finish()`.
- **Xong khi:** chọn xong, `StockInActivity` hiện đúng sản phẩm; bấm Back ở màn chọn thì không có gì thay đổi.

### Bài 5: Form nhập liệu + lưu (validation, POST)
- **Nexacro:** form nhập, `gfn_isNull` / validation, `transaction` gửi `ds_input:U`, callback báo "Lưu thành công".
- **Buổi học:** 3, 4.
- **Làm:** trên `StockInActivity` thêm ô "Số lượng" (`inputType="number"`), "Ghi chú", nút "Lưu". Backend: `POST /api/stock-in` nhận `{barcode, quantity, note}`, kiểm tra `quantity > 0`, cộng vào tồn kho mock, trả 400 kèm message nếu sai.
- **Gợi ý:** kiểm tra phía app trước (`edt.setError(...)`), phía server kiểm tra lại. Đọc message lỗi từ `response.errorBody().string()`. Disable nút Lưu trong lúc gửi.
- **Xong khi:** lưu xong quay lại tra cứu thấy tồn kho tăng; nhập 0 hoặc để trống bị chặn; tắt mạng thì báo lỗi, không crash.

### Bài 6: Quét barcode vào ô tìm kiếm
- **Nexacro:** thường là Edit nhận dữ liệu từ scanner (keyboard wedge) hoặc gọi plugin.
- **Buổi học:** 13.
- **Làm:** nút "Quét" cạnh ô từ khoá. Dùng lại cách của `QrScanActivity`: camera (ZXing) trên máy ảo / điện thoại, broadcast intent của scanner cứng trên PDA. Quét xong điền vào ô và tìm luôn; nếu đúng 1 kết quả thì mở chi tiết.
- **Gợi ý:** mở `QrScanActivity.java`, tìm chỗ đọc barcode từ Intent của scanner; tách thành hàm dùng chung được.
- **Xong khi:** quét mã `8851993123456` (tạo mã QR chứa chuỗi này từ web bất kỳ) ra đúng "Sữa TH True Milk".

### Bài 7: Nhiều dữ liệu: phân trang
- **Nexacro:** Grid tải hàng nghìn dòng, hoặc tự làm nút "Xem thêm".
- **Buổi học:** 3, 4.
- **Làm:** backend sinh thêm khoảng 500 sản phẩm giả, API nhận `page`, `size` (mặc định 20) và trả `{ "items": [...], "total": 507 }`. Android: cuộn tới cuối danh sách thì tải trang tiếp.
- **Gợi ý:** `rv.addOnScrollListener(...)` + `layoutManager.findLastVisibleItemPosition()`; cờ `loading` để không gọi trùng; Adapter thêm hàm `append(list)` dùng `notifyItemRangeInserted`.
- **Xong khi:** cuộn mượt tới dòng 500; đổi từ khoá thì danh sách làm lại từ trang 0.

### Bài 8: Giữ dữ liệu khi xoay màn hình (ViewModel)
- **Nexacro:** không có khái niệm này; đây là khác biệt lớn nhất của Android.
- **Buổi học:** 2.
- **Làm:** hiện tại xoay màn hình thì `onCreate` chạy lại và gọi API lại từ đầu. Chuyển danh sách + trạng thái loading vào `ViewModel` + `LiveData`.
- **Gợi ý:** thêm `androidx.lifecycle:lifecycle-viewmodel` và `lifecycle-livedata`; Activity chỉ `observe` và vẽ.
- **Xong khi:** tìm "sữa", xoay màn hình → vẫn 1 kết quả, không gọi lại API (xem Logcat OkHttp).

---

## 5. Quy trình khi có màn hình Nexacro thật

1. Copy file `.xfdl` (và `.xjs` dùng chung nếu cần) vào `nexacro-sample/`. **Bỏ** mật khẩu, IP / URL nội bộ, dữ liệu khách hàng.
2. Liệt kê theo mẫu mục 3: component, Dataset, transaction (URL + in/out), sự kiện, validation, popup.
3. Mỗi `transaction` → 1 API JSON: controller mới gọi lại service có sẵn (xem `ProductSearchXapiController.java.txt`).
4. Thiết kế lại cho màn PDA: ít cột, dạng thẻ, ưu tiên quét barcode.
5. Viết model + API interface → layout → Activity → Adapter.
6. Ghi tiến độ vào bảng ở [NEXACRO_TO_ANDROID.md](NEXACRO_TO_ANDROID.md) mục 8.
7. Commit, nhờ review.

---

## 6. Câu hỏi hay gặp

**Sao không dùng DataBinding cho giống BindItem?**
Được, nhưng nên học cách "thủ công" trước (findViewById / ViewBinding) để hiểu vòng đời. DataBinding thêm 1 lớp sinh code, khó debug với người mới.

**Kotlin hay Java?**
Tài liệu Android mới chủ yếu dùng Kotlin, nhưng Java vẫn được hỗ trợ đầy đủ. Project này dùng Java để bro tập trung học Android trước. Có thể chuyển dần sang Kotlin sau; 2 ngôn ngữ dùng chung được trong 1 project.

**Có nên dùng Jetpack Compose thay layout XML?**
Compose là hướng mới của Google. Tuy nhiên layout XML gần với cách nghĩ "form + component" của Nexacro hơn và project hiện dùng XML. Học XML trước, Compose tính sau.
