# Kiểu code "Java thuần" (giống code của khách) ↔ kiểu Retrofit

Code Android của khách không dùng Retrofit / Gson / RecyclerView mà dùng:

- gọi API bằng **thread + `HttpURLConnection`**, đọc JSON bằng `org.json`;
- **cất view vào `HashMap`** rồi `setOnClickListener` (thường 1 listener chung).

Repo này có **2 bản cùng chức năng hủy hàng** để đọc song song:

| Việc | Kiểu khách (Java thuần) | Kiểu Retrofit |
|---|---|---|
| Gọi server | `plain/HttpTask.java` | `api/ApiClient.java`, `AuthInterceptor`, `TokenAuthenticator`, `api/DisposalApi.java` |
| JSON → dữ liệu | `plain/JsonRows.java` (`List<HashMap<String,String>>`) | `model/Disposal*Dto.java` (Gson tự map) |
| Màn danh sách | `plain/PlainDisposalListActivity.java` + `activity_plain_disposal_list.xml` + `item_plain_row.xml` | `disposal/DisposalListActivity.java` + `DisposalListAdapter.java` |
| Màn chi tiết + xác nhận / hủy | `plain/PlainDisposalDetailActivity.java` + `activity_plain_disposal_detail.xml` | `disposal/DisposalDetailActivity.java` + `DisposalLineAdapter.java` |

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

## 3. Danh sách: ListView + SimpleAdapter (thay RecyclerView)

```java
adapter = new SimpleAdapter(this, rows, R.layout.item_plain_row,
        new String[]{"line1", "line2", "line3"},          // key trong HashMap
        new int[]{R.id.txtLine1, R.id.txtLine2, R.id.txtLine3}); // TextView nhận giá trị
```

- `rows` là `List<HashMap<String,String>>`: rất giống **Dataset** của Nexacro (dòng = HashMap, cột = key).
- Cập nhật: sửa `rows` rồi `adapter.notifyDataSetChanged()` (≈ Grid vẽ lại khi Dataset đổi).
- Chỉ gán được chữ. Muốn tô màu từng dòng → phải viết adapter riêng (`BaseAdapter` + `getView`), như `DisposalListAdapter` bản Retrofit làm trong `onBindViewHolder`.
- Ở `JsonRows.row`: giá trị `null` của JSON đổi thành `""` để `SimpleAdapter` không hiện chữ "null".

---

## 4. JUnit với org.json

`org.json` nằm trong Android, nhưng khi chạy **JUnit trên máy (test/)** nó chỉ là bản rỗng: gọi `new JSONObject(...)` sẽ ném `RuntimeException("Stub!")` hoặc trả giá trị mặc định. Muốn test `JsonRows` trên JVM:

- thêm `testImplementation "org.json:json:<version>"` (cần repo nội bộ có thư viện này), hoặc
- tách phần logic không đụng JSON (tính tiền, kiểm tra quy tắc) ra lớp Java thuần rồi test lớp đó.

---

## 5. Đọc 2 bản thế nào cho nhanh

1. Mở `plain/PlainDisposalListActivity.java` và `disposal/DisposalListActivity.java` cạnh nhau (chuột phải tab → *Split Right*).
2. Đi theo 1 lần bấm: nút → `onClick` → `search()` → `HttpTask.get` → `onSuccess` → `JsonRows.rows` → `adapter.notifyDataSetChanged()`.
3. Làm lại với bản Retrofit: nút → listener → `load()` → `DisposalApi.list` → `onResponse` → `adapter.submit(...)`.
4. Làm lại với `PlainDisposalDetailActivity.send(...)` ↔ `DisposalDetailActivity.run(...)`: 2 bản xử lý lỗi 409 / mất mạng giống nhau.
