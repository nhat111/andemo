# Đối chiếu Nexacro → Android (Java)

> Dành cho dev đã quen Nexacro + Java backend, chuẩn bị migrate màn hình Nexacro sang app Android native.
> Ví dụ Android dùng Java, Retrofit, layout XML, giống project `andemo`.
> Cập nhật: 2026-09-30. Dự án dùng **Nexacro 17 (mobile)**; cú pháp Nexacro trong tài liệu theo bản 17. Chưa dựa trên mã nguồn thật của dự án; khi có file `.xfdl` thật sẽ bổ sung.

---

## 1. Ý tưởng chính

| Nexacro | Android | Khác biệt quan trọng |
|---|---|---|
| 1 app chạy trên runtime/trình duyệt, form nạp động theo URL | 1 file APK, màn hình biên dịch sẵn | Không có "nạp form từ server"; mỗi thay đổi UI phải build và cài lại app |
| Dataset là trung tâm: UI bind trực tiếp vào Dataset | Dữ liệu là object Java (POJO); UI cập nhật qua Adapter / code | Không có binding tự động kiểu Binditem (trừ khi dùng DataBinding) |
| `transaction()` gửi/nhận Dataset (XML/SSV) | Retrofit gọi REST, nhận JSON → POJO (Gson) | Nên thêm API JSON phía server (mục 5) |
| Form sống tới khi đóng | Activity có **vòng đời**: xoay màn hình, về background, bị hệ thống kill | Phải lưu trạng thái, không giữ tham chiếu Activity trong callback lâu |
| Script chạy trên 1 luồng, `transaction` async có callback | Không được gọi mạng trên main thread; callback Retrofit `enqueue` trả về main thread | Giống ý tưởng callback của Nexacro |

---

## 2. Bảng đối chiếu khái niệm

### 2.1 Cấu trúc ứng dụng

| Nexacro | Android | Ghi chú / file trong `andemo` |
|---|---|---|
| Application (`.xadl`) | `AndroidManifest.xml` + (tuỳ chọn) lớp `Application` | Khai báo màn hình, quyền, service |
| TypeDefinition: Services (`svc::`, `lib::`, `frm::`) | `BuildConfig.API_BASE_URL` + package Java | `apiBaseUrl` trong `gradle.properties`, `api/ApiClient.java` |
| Environment, biến toàn cục `gv_*` | `SharedPreferences`, singleton, `ViewModel` dùng chung | `util/PreferenceManager.java` (token, role, username) |
| Dataset toàn cục `gds_*` (mã chung, menu, user) | Cache trong singleton / Room database | Load 1 lần sau login, lưu cục bộ nếu cần offline |
| FrameSet / ChildFrame / MDI | Không có MDI. Dùng Activity + Fragment, Navigation, BottomNavigation / Drawer | PDA màn nhỏ: 1 màn hình tại 1 thời điểm |
| Thư viện chung `lib::common.xjs` (`gfn_*`) | Lớp util tĩnh, `BaseActivity` | `util/` |
| Theme (`.xtheme`, css) | `res/values/themes.xml`, `colors.xml`, `dimens.xml` | |
| Đa ngôn ngữ | `res/values/strings.xml`, `values-vi/`, `values-ko/`… | |

### 2.2 Màn hình và thành phần

| Nexacro | Android | Ghi chú |
|---|---|---|
| Form (`.xfdl`) | `Activity` + layout XML (`res/layout/activity_xxx.xml`) | Ví dụ: `requester/FindPdaActivity.java` + `activity_find_pda.xml` |
| Div có `url` (form con) | `Fragment` hoặc `<include layout=...>` | Fragment khi form con có logic riêng |
| Tab / Tabpage | `TabLayout` + `ViewPager2` + Fragment | |
| Popup (`showModal`, `nexacro.open`) | `AlertDialog`, `DialogFragment`, hoặc Activity mở bằng `ActivityResultLauncher` | Giá trị trả về khi `this.close(ret)` → `setResult()` + `finish()` |
| **Mobile:** 1 ChildFrame, đổi form bằng `this.go("frm::…")`, truyền dữ liệu qua `gv_*` | Mỗi form → 1 Activity; `startActivity(Intent.putExtra(…))` | Không dùng biến toàn cục để truyền dữ liệu: Android có thể kill app, biến static mất |
| **Mobile:** "popup" bằng Div ẩn / hiện trong form (`div.set_visible(true)`) | `AlertDialog` với `setView(...)`, hoặc `BottomSheetDialog` | Ví dụ: ô nhập lý do trong `nexacro-sample/disposal/form/frm_disposal_detail.xfdl` |
| PopupDiv | `PopupWindow` / `BottomSheetDialog` | |
| Grid | `RecyclerView` + `Adapter` + `ViewHolder` | Xem mục 4.2. Grid nhiều cột trên PDA nên đổi thành dạng "thẻ" (mỗi dòng 2–3 dòng chữ) |
| Grid format nhiều band (head/body/summ) | Header: view riêng phía trên; summary: `TextView` phía dưới | |
| Cell đổi màu theo dữ liệu: `cssclass="expr:…"` + class trong `.xcss` | Trong `onBindViewHolder`: `setTextColor(...)` theo dữ liệu | Luôn gán cả 2 nhánh (ViewHolder bị tái sử dụng) |
| Dataset `useclientlayout="true"` + cột client tự tính (ví dụ `SHORT_YN`) | Field tính toán trong model, hoặc tính khi bind | Quy tắc tính nằm trong script: phải chép sang, hoặc đưa về server |
| Static | `TextView` | |
| Edit / TextArea | `EditText` (bọc `TextInputLayout` nếu dùng Material) | `inputType` = number, text, password… |
| MaskEdit | `EditText` + `inputType` + `TextWatcher` định dạng | |
| Combo (`innerdataset`, `codecolumn`, `datacolumn`) | `Spinner` + `ArrayAdapter`, hoặc `AutoCompleteTextView` (Material dropdown) | Lưu cả code và tên trong object, hiển thị tên |
| Radio / CheckBox | `RadioGroup` + `RadioButton` / `CheckBox` | |
| Calendar | `DatePickerDialog` / `MaterialDatePicker` | |
| Button | `Button` / `MaterialButton` | |
| ImageViewer | `ImageView` + Glide | `andemo` đã dùng Glide cho ảnh sản phẩm |
| WebBrowser | `WebView` | |
| ProgressBar / wait cursor | `ProgressBar`, disable nút trong lúc gọi API | |

### 2.3 Sự kiện

| Nexacro | Android |
|---|---|
| `form_onload` | `onCreate` (Activity) / `onViewCreated` (Fragment) |
| Form được hiện lại (activate) | `onResume` |
| `onclick` | `button.setOnClickListener(v -> ...)` |
| `onchanged` / `oncolumnchanged` | `TextWatcher`, `OnItemSelectedListener`, hoặc observe `LiveData` |
| Grid `oncellclick` / `oncelldblclick` | Listener trong Adapter (`itemView.setOnClickListener`) |
| Dataset `onrowposchanged` | Không có "row position"; lưu item đang chọn trong biến / ViewModel |
| `onkeydown` Enter | `setOnEditorActionListener` (IME action) |
| Nút Back / đóng form (`onbeforeclose`) | `OnBackPressedDispatcher` / `onDestroy` |
| Timer (`setTimer` / `ontimer`) | `Handler.postDelayed`; việc nền lâu thì dùng `WorkManager` / Service |

### 2.4 Hàm hay dùng

| Nexacro | Android |
|---|---|
| `this.alert(msg)` | `Toast.makeText(...)` (thông báo nhanh) / `AlertDialog` (cần bấm OK) |
| `this.confirm(msg)` | `new AlertDialog.Builder(this).setMessage(msg).setPositiveButton(...).setNegativeButton(...)` |
| `this.transaction(...)` | Retrofit `call.enqueue(new Callback<>() {...})` |
| `gfn_isNull(v)` | `TextUtils.isEmpty(v)` / `v == null || v.trim().isEmpty()` |
| `trace(...)` | `Log.d(TAG, ...)`, xem trong Logcat |
| `ds.getColumn(row, "COL")` | `list.get(row).getCol()` |
| `ds.setColumn(row, "COL", v)` | `list.get(row).setCol(v); adapter.notifyItemChanged(row);` |
| `ds.addRow()` / `ds.deleteRow(row)` | `list.add(obj)` / `list.remove(row)` + `adapter.notifyItem...` |
| `ds.getRowCount()` | `list.size()` |
| `ds.filter("...")` / `ds.keystring = "S:+COL"` | `stream().filter(...)` / `list.sort(Comparator.comparing(...))`; hoặc lọc/sắp xếp phía server |
| `ds.getRowType(row)` (insert/update/delete) | Tự đánh dấu trong model (mục 4.3) |
| `nexacro.getApplication().gv_userId` | `new PreferenceManager(context).getUsername()` |

---

## 3. Luồng gọi server: `transaction` → Retrofit

### Nexacro

```javascript
this.fn_search = function () {
    this.ds_search.setColumn(0, "STORE_CD", this.edt_store.value);
    this.transaction("search",
        "svc::product/search.do",
        "ds_search=ds_search",       // Dataset gửi đi
        "ds_list=ds_list",           // Dataset nhận về
        "",
        "fn_callback");
};

this.fn_callback = function (svcID, errorCode, errorMsg) {
    if (errorCode < 0) { this.alert(errorMsg); return; }
    if (svcID == "search") { trace("rows: " + this.ds_list.getRowCount()); }
};
```

### Android (Java + Retrofit)

1. **Model** (thay cho cột của Dataset):
   ```java
   public class Product {
       private String productCode;
       private String productName;
       private int qty;
       // getter / setter
   }
   ```
2. **Khai báo API** (thay cho `svc::product/search.do`):
   ```java
   public interface ProductApi {
       @GET("api/products")
       Call<List<Product>> search(@Query("storeCode") String storeCode);
   }
   ```
3. **Gọi API** trong Activity (thay cho `transaction` + `fn_callback`):
   ```java
   private void search() {
       String store = edtStore.getText().toString().trim();
       if (store.isEmpty()) { edtStore.setError("Nhập mã cửa hàng"); return; }

       progress.setVisibility(View.VISIBLE);
       ProductApi api = ApiClient.getClient(this).create(ProductApi.class);
       api.search(store).enqueue(new Callback<List<Product>>() {
           @Override public void onResponse(Call<List<Product>> call, Response<List<Product>> res) {
               progress.setVisibility(View.GONE);
               if (!res.isSuccessful() || res.body() == null) {        // ~ errorCode < 0
                   Toast.makeText(ProductListActivity.this, "Lỗi " + res.code(), Toast.LENGTH_SHORT).show();
                   return;
               }
               adapter.submit(res.body());                              // ~ ds_list nhận dữ liệu
           }
           @Override public void onFailure(Call<List<Product>> call, Throwable t) {  // mất mạng, timeout
               progress.setVisibility(View.GONE);
               Toast.makeText(ProductListActivity.this, "Không kết nối được server", Toast.LENGTH_SHORT).show();
           }
       });
   }
   ```

| Nexacro | Retrofit |
|---|---|
| `strSvcID` để phân biệt trong callback | Mỗi lời gọi có callback riêng, không cần svcID |
| In-Dataset | `@Query`, `@Path`, `@Body` (object Java → JSON) |
| Out-Dataset | Kiểu trả về `Call<T>` (JSON → object Java) |
| `strArgument` (`a=b c=d`) | `@Query` / field trong `@Body` |
| `errorCode`, `errorMsg` | HTTP status (`res.code()`), body lỗi; `onFailure` khi lỗi mạng |
| Session / cookie (JSESSIONID sau `login.do`) | API mới: JWT trong header `Authorization` (`ApiClient` + `TokenAuthenticator`). Gọi thẳng `*.do` cũ: OkHttp `CookieJar` giữ cookie, `ErrorCode -99` → login lại |
| `bAsync = false` (đồng bộ) | **Không làm trên main thread.** Nếu cần tuần tự: gọi API tiếp theo trong `onResponse` của API trước |

---

## 4. Mẫu chuyển đổi thường gặp

### 4.1 Form tìm kiếm + Grid kết quả

| Nexacro | Android |
|---|---|
| `Edit` điều kiện + nút Tìm | `EditText` + `Button` trong `activity_product_list.xml` |
| `ds_search` | Tham số `@Query` |
| `ds_list` bind vào Grid | `List<Product>` trong Adapter của `RecyclerView` |
| Click dòng → mở popup chi tiết | Listener trong Adapter → `startActivity(intent.putExtra("productCode", ...))` |

### 4.2 Grid → RecyclerView

Cần 3 phần: layout 1 dòng (`item_product.xml`), `Adapter`, và gắn vào `RecyclerView`.

```java
public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
    public interface OnClick { void onClick(Product p); }

    private final List<Product> items = new ArrayList<>();
    private final OnClick onClick;

    public ProductAdapter(OnClick onClick) { this.onClick = onClick; }

    public void submit(List<Product> data) {          // ~ ds_list.copyData(...)
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {   // ~ Grid format: cột → view
        Product p = items.get(position);
        h.txtCode.setText(p.getProductCode());
        h.txtName.setText(p.getProductName());
        h.txtQty.setText(String.valueOf(p.getQty()));
        h.itemView.setOnClickListener(v -> onClick.onClick(p));  // ~ oncellclick
    }

    @Override public int getItemCount() { return items.size(); }  // ~ getRowCount()

    static class VH extends RecyclerView.ViewHolder {
        final TextView txtCode, txtName, txtQty;
        VH(View v) {
            super(v);
            txtCode = v.findViewById(R.id.txtCode);
            txtName = v.findViewById(R.id.txtName);
            txtQty = v.findViewById(R.id.txtQty);
        }
    }
}
```

Trong Activity:
```java
RecyclerView rv = findViewById(R.id.rvProducts);
rv.setLayoutManager(new LinearLayoutManager(this));
adapter = new ProductAdapter(p -> openDetail(p.getProductCode()));
rv.setAdapter(adapter);
```

Cần thêm thư viện: `implementation 'androidx.recyclerview:recyclerview:1.3.2'` (hoặc dùng sẵn qua `com.google.android.material`).

### 4.3 Lưu thay đổi (rowtype insert / update / delete)

Nexacro tự theo dõi `rowtype` và chỉ gửi dòng thay đổi (`ds_list:U`). Android không có sẵn, cách đơn giản:

```java
public class Product {
    public enum RowState { NORMAL, INSERTED, UPDATED, DELETED }
    private RowState state = RowState.NORMAL;
    // khi sửa field: if (state == NORMAL) state = UPDATED;
}
```
Khi lưu: lọc các dòng khác `NORMAL`, gửi 1 request `@Body List<Product>` (hoặc tách 3 danh sách insert / update / delete) lên API lưu. Server xử lý trong 1 transaction DB như code Nexacro cũ.

### 4.4 Combo mã chung

| Nexacro | Android |
|---|---|
| `gds_code` load sau login, Combo `innerdataset` lọc theo nhóm mã | Gọi API mã chung 1 lần, lưu trong singleton (hoặc Room nếu cần offline), `ArrayAdapter<CodeItem>` với `toString()` trả về tên |
| `codecolumn` / `datacolumn` | Lấy `CodeItem.getCode()` khi lưu, hiện `getName()` |

### 4.5 Popup trả kết quả

Nexacro: `showModal(...)`, popup gọi `this.close(ret)`, form cha nhận trong callback.

Android:
```java
private final ActivityResultLauncher<Intent> pickProduct =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                String code = result.getData().getStringExtra("productCode");
                edtProduct.setText(code);
            }
        });

// mở popup
pickProduct.launch(new Intent(this, ProductPickerActivity.class));

// trong ProductPickerActivity, khi chọn xong
setResult(RESULT_OK, new Intent().putExtra("productCode", code));
finish();
```
Hộp thoại đơn giản (chọn 1 trong vài giá trị) thì dùng `AlertDialog.setItems(...)` là đủ.

---

## 5. Phía server

Nexacro gọi `*.do`, server dùng X-API (`PlatformData`, `DataSet`, `VariableList`) đọc/ghi XML hoặc SSV. Android native nên gọi **REST JSON**.

| Cách | Làm gì | Khuyến nghị |
|---|---|---|
| **Thêm controller JSON song song** | `@RestController` mới gọi lại **service / DAO / mapper hiện có**; chỉ đổi lớp vào/ra (DTO ↔ JSON) | ✅ Nên dùng. Web Nexacro cũ vẫn chạy nguyên, 2 bên dùng chung nghiệp vụ |
| Android parse XML Dataset | Viết parser trên app | ❌ Tốn công, khó bảo trì, không tận dụng được Gson/Retrofit |
| Viết lại toàn bộ backend | | ❌ Không cần cho việc chuyển UI |

Map dữ liệu:
- 1 Dataset nhiều dòng → `List<Dto>`.
- Dataset 1 dòng (điều kiện tìm) → 1 `Dto` hoặc `@RequestParam`.
- `VariableList` → field trong DTO / query param.
- `ErrorCode` / `ErrorMsg` → HTTP status + body `{ "message": "..." }`.
- Đăng nhập / session → JWT (backend `andemo` đã có login + refresh token, dùng lại được).

---

## 6. Quy trình migrate 1 màn hình

1. **Đọc form Nexacro:** liệt kê component, Dataset (cột, kiểu), các `transaction` (URL, in/out), sự kiện, validation, popup.
2. **Xác định API:** mỗi `transaction` → 1 endpoint JSON (mục 5). Test bằng curl / Postman trước.
3. **Thiết kế lại cho màn PDA:** màn nhỏ, thao tác một tay, ưu tiên quét barcode thay vì gõ. Bỏ bớt cột Grid, đưa chi tiết sang màn khác.
4. **Tạo model + API interface** (mục 3).
5. **Layout XML + Activity/Fragment**, gắn sự kiện (mục 2.3).
6. **Validation, thông báo lỗi, trạng thái loading.**
7. **Test trên máy thật / máy ảo cấu hình giống PDA:** xoay màn hình, về background rồi quay lại, mất mạng giữa chừng.
8. Cập nhật bảng tiến độ (mục 8).

---

## 7. Lỗi hay gặp khi chuyển từ Nexacro

| Lỗi | Vì sao | Cách tránh |
|---|---|---|
| App crash `NetworkOnMainThreadException` | Gọi mạng đồng bộ (`execute()`) trên main thread | Dùng `enqueue`, hoặc chạy trong thread nền |
| Xoay màn hình là mất dữ liệu đã tải | Activity bị tạo lại | Khoá hướng màn hình (PDA thường dọc), hoặc giữ dữ liệu trong `ViewModel` |
| Crash khi callback về sau khi đã thoát màn hình | Activity đã `finish` nhưng callback vẫn cập nhật view | Kiểm tra `isFinishing()` / `isDestroyed()` trong callback, hoặc dùng ViewModel + LiveData |
| Grid hàng nghìn dòng rất chậm | Tải hết 1 lần | Phân trang phía server (`page`, `size`), `RecyclerView` chỉ vẽ dòng đang thấy |
| Sai định dạng ngày / số | Nexacro tự format theo mask | Thống nhất: server gửi ISO (`2026-09-29`, `2026-09-29T10:00:00Z`), app format khi hiển thị |
| Tưởng biến toàn cục luôn còn | Android có thể kill app khi ở background, biến static mất | Lưu thông tin cần giữ vào `SharedPreferences` / database |
| Quên quyền (camera, thông báo…) | Nexacro chạy trong trình duyệt/runtime lo phần này | Khai báo trong Manifest + xin quyền lúc chạy (xem bug A3 trong `ANDROID_FIX_TRACKER.md`) |

---

## 8. Bảng theo dõi migrate (mẫu)

| # | Màn hình Nexacro (`.xfdl`) | Chức năng | Transaction → API JSON | Màn Android | Hướng (native / WebView) | Trạng thái |
|---|---|---|---|---|---|---|
| 1 | `frm_login.xfdl` (ví dụ) | Đăng nhập | `login.do` → `POST /api/auth/login` (đã có) | `LoginActivity` (đã có) | Native | ✅ |
| 2 | | | | | | ⬜ |
| 3 | | | | | | ⬜ |

---

## 9. Học thêm

- `docs/ANDROID_LEARNING_PLAN.md`: buổi 2 (Activity, vòng đời), buổi 3 (layout, danh sách, dialog), buổi 4 (Retrofit), buổi 10 (lưu dữ liệu cục bộ).
- Đọc code mẫu trong project: `LoginActivity` (form + gọi API), `requester/FindPdaActivity.java` (danh sách + gọi API + polling kết quả), `api/ApiClient.java` (cấu hình Retrofit, token).
