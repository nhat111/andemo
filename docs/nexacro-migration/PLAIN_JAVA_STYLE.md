# Kiểu code "Java thuần" (giống code của khách) ↔ kiểu Retrofit

> 📚 [Mục lục tài liệu](README.md) · Lộ trình: [2 tuần](LEARNING_PLAN_2_WEEKS.md) · Làm 1 màn: [công thức](HOW_TO_CODE_A_SCREEN.md)

Code Android của khách không dùng Retrofit / Gson / RecyclerView mà dùng:

- gọi API bằng **thread + `HttpURLConnection`**, đọc JSON bằng `org.json`;
- **cất view vào `HashMap`** rồi `setOnClickListener` (thường 1 listener chung).

Repo này có **2 bản cùng chức năng hủy hàng** để đọc song song:

| Việc | Kiểu khách (Java thuần) | Kiểu Retrofit |
|---|---|---|
| Gọi server | `plain/HttpTask.java` | `api/ApiClient.java`, `AuthInterceptor`, `TokenAuthenticator`, `api/DisposalApi.java` |
| JSON → dữ liệu | `plain/JsonRows.java` (`List<HashMap<String,String>>`) | `model/Disposal*Dto.java` (Gson tự map) |
| Màn danh sách | `plain/PlainDisposalListActivity.java` + `PlainDisposalListAdapter.java` + `activity_plain_disposal_list.xml` + `item_plain_disposal.xml` | `disposal/DisposalListActivity.java` + `DisposalListAdapter.java` |
| Màn chi tiết + xác nhận / hủy | `plain/PlainDisposalDetailActivity.java` + `PlainDisposalLineAdapter.java` + `activity_plain_disposal_detail.xml` + `item_plain_line.xml` | `disposal/DisposalDetailActivity.java` + `DisposalLineAdapter.java` |
| Màn "Lưu hủy": Tìm → **dialog ListView** chọn phiếu → chi tiết lên ListView → bấm dòng sửa SL (dialog) → Lưu | `plain/PlainDisposalSaveActivity.java` + `activity_plain_disposal_save.xml` (dùng lại `PlainDisposalListAdapter`, `PlainDisposalLineAdapter`) | – |

Mở app → nút **"Phiếu hủy hàng (Java thuần)"**. Cùng backend, cùng API `/api/disposals/**`, nên kết quả phải giống bản Retrofit.

> Khi vào dự án: **viết theo kiểu của khách** (đồng bộ code base quan trọng hơn sở thích). Bản Retrofit chỉ để hiểu khái niệm và đọc tài liệu trên mạng.

---

## 1. Gọi API: `HttpTask`

```java
HttpTask.get(this, "api/disposals?status=REGISTERED", new HttpTask.Callback() {
    @Override public void onSuccess(String body) { /* luồng giao diện: được đụng view */ }
    @Override public void onError(int httpCode, String message) { /* 0 = không tới được server */ }
});
```

Bên trong (`HttpTask.call`):

1. `EXECUTOR.execute(...)`: chạy ở **luồng nền** (Android cấm gọi mạng trên luồng giao diện → `NetworkOnMainThreadException`).
2. `HttpURLConnection`: set method, timeout, `Authorization: Bearer <token>`, ghi body UTF-8.
3. `code < 400 ? getInputStream() : getErrorStream()`: lỗi 4xx/5xx phải đọc **errorStream**, đọc inputStream sẽ ném `IOException`.
4. `MAIN.post(...)`: đưa kết quả về **luồng giao diện** (đụng view từ luồng nền → `CalledFromWrongThreadException`).
5. Gặp 401 → làm mới token 1 lần rồi gọi lại (bản Retrofit làm việc này trong `TokenAuthenticator`).

So với Nexacro: `HttpTask.get/post` ≈ `this.transaction(...)`; `Callback` ≈ `fn_callback`; `onError` ≈ nhánh `errorCode < 0`.

| Retrofit | Java thuần |
|---|---|
| `@GET("api/disposals") Call<List<Dto>> list(@Query("status") ...)` | `HttpTask.get(ctx, "api/disposals?status=" + s, cb)` |
| `enqueue` → `onResponse` (có trả lời) / `onFailure` (mất mạng) | `onSuccess` (2xx) / `onError(code, msg)`, code 0 = mất mạng |
| Gson: JSON → `DisposalSummaryDto` | `new JSONArray(body)` → `JsonRows.rows(...)` → `HashMap` |
| `AuthInterceptor` thêm header | `conn.setRequestProperty("Authorization", ...)` trong `call` |
| `HttpLoggingInterceptor` (Logcat `okhttp`) | `Log.d("HttpTask", ...)` (Logcat lọc `tag:HttpTask`) |

### Lỗi hay gặp khi tự viết thread + HttpURLConnection

| Lỗi | Hậu quả | Cách tránh (đã làm trong `HttpTask`) |
|---|---|---|
| Gọi mạng trên luồng giao diện | Crash `NetworkOnMainThreadException` | Luôn qua `EXECUTOR` |
| `setText` trong luồng nền | Crash `CalledFromWrongThreadException` | `MAIN.post` / `runOnUiThread` |
| `new Thread()` mỗi lần bấm | Bấm nhiều = nhiều luồng, khó kiểm soát | Thread pool dùng chung |
| Không set timeout | Mạng chập chờn → treo mãi | `setConnectTimeout` / `setReadTimeout` |
| Đọc `getInputStream()` khi 4xx | `IOException`, mất thông báo lỗi của server | `getErrorStream()` khi `code >= 400` |
| Không chỉ định UTF-8 | Tiếng Hàn / Việt bị vỡ chữ | `StandardCharsets.UTF_8` khi ghi và đọc |
| Không đóng stream / `disconnect()` | Rò kết nối | `try-with-resources` + `finally` |
| Callback về khi màn đã đóng | Crash khi mở dialog | Kiểm tra `isFinishing()` / `isDestroyed()` đầu callback |
| Bấm "Xác nhận" 2 lần | Gửi 2 request | Tắt nút khi gửi (`setEnabled(false)`), server có `version` chặn thêm |
| Mất mạng giữa chừng khi POST | Không biết server đã xử lý chưa | Báo lỗi + **tải lại** phiếu, không tự gửi lại |

---

## 2. View cất trong HashMap + 1 OnClickListener chung

```java
public class PlainDisposalListActivity extends AppCompatActivity implements View.OnClickListener {
    private final HashMap<String, View> viewMap = new HashMap<>();

    protected void onCreate(Bundle b) {
        ...
        viewMap.put("btnReg", findViewById(R.id.btnReg));
        viewMap.put("tvCount", findViewById(R.id.tvCount));
        for (String key : new String[]{"btnReg", "btnCfm", "btnAll", "btnRefresh"}) {
            viewMap.get(key).setOnClickListener(this);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnReg) { ... }
        else if (id == R.id.btnCfm) { ... }
    }
}
```

- Lấy view theo tên: `((TextView) viewMap.get("tvCount")).setText(...)`. Phải **ép kiểu**; sai kiểu chỉ lộ ra lúc chạy (`ClassCastException`). `PlainDisposalDetailActivity` tách sẵn `HashMap<String, TextView>` và `HashMap<String, Button>` để đỡ ép kiểu.
- Gõ sai key (`"tvCont"`) → `get` trả `null` → `NullPointerException` lúc chạy, compiler không báo. Nên đặt key **trùng id trong XML**.
- Nexacro: `this.btn_search_onclick` gắn qua `onclick=` trong xfdl. Ở đây: 1 hàm `onClick` chung, phân nhánh theo id.

### Bẫy: `switch (v.getId())` báo lỗi build

Code cũ hay viết:

```java
switch (v.getId()) {
    case R.id.btnReg: ...   // lỗi: "constant expression required"
}
```

Từ **Android Gradle Plugin 8** (`android.nonFinalResIds=true` mặc định), `R.id.xxx` không còn là `final` nên **không dùng được trong `case`**. Dùng `if / else if` như trong repo. Nếu code của khách đang dùng `switch` mà vẫn build được, dự án của khách đang dùng AGP 7 hoặc đã đặt `android.nonFinalResIds=false` trong `gradle.properties`: xem file đó, **đừng tự đổi**.

---

## 3. Danh sách: ListView + adapter tự viết (BaseAdapter + ViewHolder)

Code thật của khách thường là `ListView` + **custom adapter** `extends BaseAdapter`. Repo có 3 lớp mẫu:

| Adapter | Dùng ở | Có gì |
|---|---|---|
| `plain/PlainDisposalLineAdapter.java` + `item_plain_line.xml` | Dòng hàng trong chi tiết | **Viết tay đủ mọi bước** (đọc để hiểu): ViewHolder, chữ đỏ khi thiếu tồn, **nút "Tồn" trong từng dòng** báo về Activity qua interface |
| `plain/HashMapListAdapter.java` | Khung dùng chung | Gom sẵn các bước lặp lại (4 hàm, `convertView`, ViewHolder) |
| `plain/PlainDisposalListAdapter.java` + `item_plain_disposal.xml` | Danh sách phiếu | Dựng trên khung: chỉ còn chọn layout + `bind`, tô màu trạng thái |

Đảm bảo không sót bước (khung, tool soát `ProjectCheck`, checklist): [WORK_WITHOUT_BUILD.md](WORK_WITHOUT_BUILD.md).

### 3.1 Khung 4 hàm của BaseAdapter

```java
public class PlainDisposalLineAdapter extends BaseAdapter {
    private final List<HashMap<String, String>> rows;   // Activity giữ, adapter chỉ đọc

    public int getCount()               { return rows.size(); }        // số dòng
    public HashMap<String,String> getItem(int p) { return rows.get(p); }
    public long getItemId(int p)        { return p; }
    public View getView(int p, View convertView, ViewGroup parent) { ... } // vẽ 1 dòng
}
```

- `rows` là `List<HashMap<String,String>>`: rất giống **Dataset** của Nexacro (dòng = HashMap, cột = key).
- Activity sửa `rows` (clear / addAll) rồi gọi `adapter.notifyDataSetChanged()` (≈ Grid vẽ lại khi Dataset đổi).
- `getView` ≈ Band body của Grid: gán cột nào vào ô nào, màu gì (≈ `cssclass="expr:..."`).

### 3.2 getView: convertView + ViewHolder

```java
public View getView(int position, View convertView, ViewGroup parent) {
    ViewHolder h;
    if (convertView == null) {                      // lần đầu: tạo view từ XML
        convertView = inflater.inflate(R.layout.item_plain_line, parent, false);
        h = new ViewHolder();
        h.txtName = convertView.findViewById(R.id.txtName);
        ...
        convertView.setTag(h);                      // cất holder vào view
    } else {
        h = (ViewHolder) convertView.getTag();      // view cũ cuộn ra khỏi màn hình được đưa lại
    }
    HashMap<String, String> r = rows.get(position);
    h.txtName.setText(r.get("itemName"));
    h.txtQty.setTextColor(shortage ? Color.RED : Color.DKGRAY);
    return convertView;
}
```

Phần này `HashMapListAdapter.getView` đã làm sẵn; adapter dựng trên khung chỉ viết phần gán dữ liệu (`bind`). ListView chỉ tạo đủ view cho số dòng nhìn thấy, cuộn thì **dùng lại** view cũ (`convertView`). ViewHolder giữ sẵn các TextView để khỏi `findViewById` mỗi lần cuộn. Ý tưởng này giống hệt `RecyclerView.ViewHolder` (`onCreateViewHolder` ≈ nhánh `convertView == null`, `onBindViewHolder` ≈ phần gán dữ liệu).

### 3.3 Nút trong dòng (setTag(position) + interface)

```java
// Adapter
public interface OnRowButtonListener { void onStockClick(int position, HashMap<String,String> row); }

h.btnStock.setOnClickListener(this);   // trong nhánh convertView == null: gắn 1 lần
h.btnStock.setTag(position);           // mỗi lần getView: ghi lại vị trí hiện tại

public void onClick(View v) {
    int position = (Integer) v.getTag();
    listener.onStockClick(position, rows.get(position));
}

// Activity: implements PlainDisposalLineAdapter.OnRowButtonListener
lineAdapter = new PlainDisposalLineAdapter(this, lines, this);
```

Adapter **không tự gọi API / mở màn hình**: nó báo về Activity, Activity quyết định (giống Grid gọi event của Form).

### 3.4 Lỗi hay gặp với custom adapter

| Lỗi | Hậu quả | Cách tránh |
|---|---|---|
| Chỉ gán màu ở 1 nhánh `if` | Cuộn lên xuống, dòng khác bị "dính" màu đỏ của dòng cũ | Gán **cả 2 nhánh** (`shortage ? RED : DKGRAY`) |
| `inflate` mỗi lần, bỏ qua `convertView` | Cuộn giật, tốn bộ nhớ | Chỉ `inflate` khi `convertView == null` |
| `inflate(id, parent)` hoặc `inflate(id, null)` | Crash "addView not supported" / mất `layout_height` của dòng | `inflate(id, parent, false)` |
| Dùng `position` cũ trong listener tạo 1 lần | Bấm dòng 10 mà xử lý dòng 1 | Ghi `setTag(position)` mỗi lần `getView`, đọc lại khi bấm |
| `new OnClickListener` trong `getView` mỗi lần | Không sai, nhưng tạo object liên tục | Gắn 1 lần khi tạo view, lấy vị trí từ tag |
| Có Button / CheckBox trong dòng → `OnItemClickListener` của ListView không chạy | Bấm dòng không có phản ứng | `android:descendantFocusability="blocksDescendants"` ở layout gốc của dòng (xem `item_plain_line.xml`) hoặc `focusable="false"` cho nút |
| Sửa `rows` ở luồng nền | Crash "The content of the adapter has changed but ListView did not receive a notification" | Chỉ sửa `rows` + `notifyDataSetChanged()` trên luồng giao diện (trong callback `HttpTask`) |
| Gán `rows = newList` trong Activity | Adapter vẫn giữ list cũ, màn hình không đổi | `rows.clear(); rows.addAll(newList);` |
| `r.get("key")` của key không có | `null` → `NullPointerException` khi `.isEmpty()` | `JsonRows.row` đổi JSON `null` thành `""`; key có thể thiếu thì kiểm tra `null` |

Danh sách rất đơn giản, chỉ có chữ thì vẫn có thể dùng `SimpleAdapter(context, rows, layout, String[] keys, int[] ids)`, nhưng hễ cần màu / ẩn hiện / nút trong dòng là phải tự viết adapter như trên.

---

## 4. JUnit với org.json

`org.json` nằm trong Android, nhưng khi chạy **JUnit trên máy (test/)** nó chỉ là bản rỗng: gọi `new JSONObject(...)` sẽ ném `RuntimeException("Stub!")` hoặc trả giá trị mặc định. Muốn test `JsonRows` trên JVM:

- thêm `testImplementation "org.json:json:<version>"` (cần repo nội bộ có thư viện này), hoặc
- tách phần logic không đụng JSON (tính tiền, kiểm tra quy tắc) ra lớp Java thuần rồi test lớp đó.

---

## 5. Đọc 2 bản thế nào cho nhanh

1. Mở `plain/PlainDisposalListActivity.java` và `disposal/DisposalListActivity.java` cạnh nhau (chuột phải tab → *Split Right*).
2. Đi theo 1 lần bấm: nút → `onClick` → `search()` → `HttpTask.get` → `onSuccess` → `JsonRows.rows` → `adapter.notifyDataSetChanged()` → `PlainDisposalListAdapter.getView`.
3. Làm lại với bản Retrofit: nút → listener → `load()` → `DisposalApi.list` → `onResponse` → `adapter.submit(...)`.
4. Làm lại với `PlainDisposalDetailActivity.send(...)` ↔ `DisposalDetailActivity.run(...)`: 2 bản xử lý lỗi 409 / mất mạng giống nhau.

---

## 6. Đọc layout XML (`res/layout/*.xml`)

Ví dụ xuyên suốt: `res/layout/activity_plain_disposal_save.xml` (màn "Lưu hủy").

### 6.1 Khác Nexacro ở đâu

Nexacro đặt vị trí **tuyệt đối**: `left="10" top="50" width="460" height="24"`.
Android **xếp nối tiếp**: `LinearLayout` xếp các view con lần lượt theo dọc / ngang; mỗi view chỉ khai báo **muốn rộng / cao bao nhiêu**, vị trí tự chạy theo thứ tự trong file.

### 6.2 Kích thước: `layout_width` / `layout_height`

| Giá trị | Nghĩa | Ví dụ |
|---|---|---|
| `match_parent` | Bằng view cha | `tvHeader` rộng hết màn hình |
| `wrap_content` | Vừa đủ nội dung | nút `Tìm` chỉ rộng bằng chữ "Tìm" |
| `120dp` | Cố định | ít dùng: mỗi PDA một cỡ màn hình |
| **`0dp`** | "Đừng tự đo, để `layout_weight` chia" | xem 6.3 |

### 6.3 `0dp` + `layout_weight`: chia phần còn trống

`layout_weight` = **đo các view khác trước, phần trống còn lại chia cho các view có weight**.
Kích thước theo **chiều đang chia** để `0dp`: "phần của tôi lấy hết từ phần chia".

**Hàng ngang** (chia chiều rộng → `layout_width="0dp"`): ô từ khóa + nút Tìm

```xml
<EditText android:layout_width="0dp" android:layout_weight="1" />   <!-- lấy hết phần còn lại -->
<Button   android:layout_width="wrap_content" />                    <!-- chỉ rộng bằng chữ -->
```

**Hàng dọc** (chia chiều cao → `layout_height="0dp"`): ListView ở giữa co giãn

```xml
<TextView android:id="@+id/tvHeader" android:layout_height="wrap_content" />
<ListView android:id="@+id/lvLines"  android:layout_height="0dp" android:layout_weight="1" />
<LinearLayout android:layout_height="wrap_content"> … nút Tải lại / Lưu … </LinearLayout>
```

→ Tiêu đề trên, nút dưới đáy, ListView lấp đầy giữa. Để `wrap_content` thì list dài đẩy nút Lưu ra khỏi màn hình.

**Chia tỉ lệ**: 2 nút cùng `0dp` + `weight="1"` → 50 / 50; weight `1` và `2` → 1/3 và 2/3.

Nhớ:
- Hàng **ngang** → `width="0dp"`; hàng **dọc** → `height="0dp"`.
- **Không dùng weight trong `ScrollView`**: không có "phần còn lại" để chia → view cao 0, biến mất. Cần chiều cao tối thiểu thì dùng `wrap_content` + `android:minHeight="160dp"` (xem `tvItems` trong `activity_main.xml`).

### 6.4 Các thông số hay gặp

| Thông số | Nghĩa |
|---|---|
| `xmlns:android="…"` | Khai báo, chỉ ở thẻ ngoài cùng. Copy nguyên |
| `android:orientation` | `vertical` trên xuống, `horizontal` trái sang phải |
| `android:id="@+id/btnSearch"` | Tên để Java tìm (`findViewById(R.id.btnSearch)`); `+` = tạo id mới. Key trong `viewMap` nên trùng tên này |
| `android:padding="12dp"` | Khoảng cách **bên trong** view (nội dung lùi vào) |
| `android:layout_margin…` | Khoảng cách **bên ngoài** view (đẩy view khác ra) |
| `dp` | Đơn vị kích thước, tự quy đổi theo mật độ màn hình. Không dùng `px` |
| `sp` | Đơn vị **cỡ chữ** (`textSize="15sp"`), to theo cài đặt cỡ chữ của máy |
| `text` / `hint` | Chữ hiển thị / chữ mờ gợi ý khi ô trống |
| `textStyle="bold"`, `textColor="#616161"` | Chữ đậm / màu (`#RRGGBB`, `#AARRGGBB` có độ trong suốt) |
| `inputType="text"` / `"number"` | Loại bàn phím; `number` = bàn phím số (≈ `inputtype="number"` của Nexacro) |
| `imeOptions="actionSearch"` | Phím Enter thành nút 🔍; Java bắt bằng `setOnEditorActionListener` |
| `maxLines="1"` | Ô nhập 1 dòng |
| `visibility` | `visible` hiện; `invisible` ẩn nhưng **giữ chỗ**; `gone` ẩn và **không chiếm chỗ** (≈ `visible="false"` của Nexacro) |
| `gravity` | Căn **nội dung bên trong** view (chữ giữa nút…) |
| `layout_gravity` | Căn **chính view đó** trong view cha |
| `minHeight` | Cao tối thiểu, nội dung nhiều thì cao thêm |
| `descendantFocusability="blocksDescendants"` | (`item_plain_line.xml`) Có nút trong dòng mà vẫn bấm được cả dòng ListView |
| `windowSoftInputMode="adjustResize"` | (trong `AndroidManifest.xml`, không phải layout) Bàn phím hiện thì màn co lại, ô đang gõ không bị che |
| `<!-- … -->` | Comment |

### 6.5 Xem trước giao diện

Mở file layout trong Android Studio → tab **Split** (góc trên phải): sửa XML bên trái, bên phải hiện ngay.
Thử đổi `weight`, đổi `0dp` ↔ `wrap_content`, đổi `gone` ↔ `invisible` để thấy khác biệt.
Chạy `devcheck\run.bat` sau khi sửa layout: `ProjectCheck` báo id sai / id không thuộc layout ([WORK_WITHOUT_BUILD.md](WORK_WITHOUT_BUILD.md) mục 2).
