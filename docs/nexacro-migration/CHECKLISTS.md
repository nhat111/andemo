# Không sót bước: khung sẵn + tool soát + checklist

Thêm 1 danh sách / 1 màn hình trên Android phải sửa nhiều chỗ (Java, layout dòng, layout màn, manifest…).
Trong VDI, code đỏ hết vì thiếu thư viện → **không nhìn được lỗi thật**, không có emulator để bấm thử.
Nên dùng 3 lớp bảo vệ, từ tự động đến thủ công:

| Lớp | Cái gì | Bắt được |
|---|---|---|
| 1. Giảm số bước | Khung `plain/HashMapListAdapter` + file template Android Studio | Không còn bước nào để quên trong phần lặp lại của adapter |
| 2. Tool tự soát | `devcheck/ProjectCheck.java` (chạy bằng `java`, không cần Gradle) | Quên khai báo manifest, sai tên layout / id, id không thuộc layout, `switch R.id`, `inflate` sai |
| 3. Checklist | Các bảng dưới | Phần tool không đọc được: luồng, logic, giao diện |

Chạy tất cả 1 lần (từ thư mục `android`): `devcheck\run.bat` (Mac / Linux: `sh devcheck/run.sh`).

---

## 1. Khung adapter: còn 2 bước

Viết tay `BaseAdapter` có ~8 bước (4 hàm, `convertView`, ViewHolder, `inflate(…, parent, false)`, `setTag`…).
`HashMapListAdapter` làm sẵn hết; adapter mới chỉ còn:

```java
public class ProductAdapter extends HashMapListAdapter {
    public ProductAdapter(Context c, List<HashMap<String, String>> rows) {
        super(c, rows, R.layout.item_product);                  // 1) layout của 1 dòng
    }

    @Override
    protected void bind(RowViews v, HashMap<String, String> row, int position) {
        v.text(R.id.txtName).setText(row.get("name"));          // 2) gán dữ liệu
        boolean out = "0".equals(row.get("stock"));
        v.text(R.id.txtStock).setTextColor(out ? Color.RED : Color.DKGRAY);   // gán cả 2 nhánh
    }
}
```

- `v.text(id)` / `v.view(id)`: tự `findViewById` lần đầu rồi nhớ lại (ViewHolder chung). Id không có trong layout dòng → báo rõ tên id thay vì `NullPointerException` mù.
- Nút trong dòng: override `onCreateRow(RowViews v)` để gắn listener 1 lần, trong `bind` gọi `v.view(R.id.btnX).setTag(position)`.
- Ví dụ thật: `plain/PlainDisposalListAdapter.java`. Bản viết tay đủ mọi bước (để hiểu bên trong): `plain/PlainDisposalLineAdapter.java`.

> Code của khách: trước khi đưa khung này vào, hỏi lead. Nếu dự án đã có adapter cha / quy ước riêng thì theo dự án; ý tưởng (gom phần lặp vào 1 lớp cha) vẫn dùng được.

### File template trong Android Studio (khỏi gõ lại khung)

*Settings → Editor → File and Code Templates → tab Files → +*. Name: `HashMap Adapter`, Extension: `java`, nội dung:

```java
package ${PACKAGE_NAME};

import android.content.Context;

import com.example.andemo.R;

import java.util.HashMap;
import java.util.List;

public class ${NAME} extends HashMapListAdapter {

    public ${NAME}(Context context, List<HashMap<String, String>> rows) {
        super(context, rows, R.layout.TODO_item_layout);
    }

    @Override
    protected void bind(RowViews v, HashMap<String, String> row, int position) {
        // TODO v.text(R.id.xxx).setText(row.get("key"));
    }
}
```

Dùng: chuột phải package → *New → HashMap Adapter* → gõ tên. `TODO_item_layout` cố tình chưa có: quên sửa thì `ProjectCheck` báo ERROR.
(Đổi `com.example.andemo.R` thành package của dự án thật.)

---

## 2. ProjectCheck: tool tự soát

Một file Java, **chỉ đọc file text** (Java, XML, manifest, `build.gradle`), không cần thư viện nào, nên chạy được cả khi project đỏ:

```bat
cd android
"C:\Program Files\Android\Android Studio\jbr\bin\java" devcheck\ProjectCheck.java
```

Soát module khác / project khác: truyền đường dẫn `src/main`:

```bat
java devcheck\ProjectCheck.java D:\work\project\app\src\main
```

Kết quả mẫu (cố ý làm sai để thấy):

```
ERROR .../PlainDisposalDetailActivity.java:32  Activity PlainDisposalDetailActivity chưa khai báo trong AndroidManifest.xml → mở màn hình sẽ crash (ActivityNotFoundException)
ERROR .../PlainDisposalListActivity.java:42  R.layout.activity_plain_disposal_lst không tồn tại trong res/ → lỗi build
ERROR .../PlainDisposalListAdapter.java:29  id txtQty không có trong layout [item_plain_disposal] → findViewById trả null → NullPointerException khi chạy
ERROR .../PlainDisposalListActivity.java:78  switch / case R.id.x: từ AGP 8 R.id không còn là hằng số → lỗi build. Dùng if / else
WARN  .../PlainDisposalLineAdapter.java:73  inflate 2 tham số: trong adapter dùng inflate(layout, parent, false)
WARN  .../PlainDisposalLineAdapter.java:94  adapter có ẩn view mà không chỗ nào hiện lại (View.VISIBLE): view dùng lại sẽ bị ẩn sai dòng

4 ERROR, 2 WARN
```

| Soát | Mức | Nếu bỏ qua thì |
|---|---|---|
| Class `extends …Activity` chưa có `<activity>` trong manifest | ERROR | Crash khi mở màn |
| `R.layout / R.id / R.string / R.color / R.drawable…` không có trong `res/` (tính cả `resValue` trong `build.gradle`) | ERROR | Lỗi build (chỉ thấy ở máy remote) |
| `@layout/ @id/ @string/…` trong XML / manifest không tồn tại | ERROR | Lỗi build |
| `findViewById(R.id.x)` / `v.text(R.id.x)` mà `x` không nằm trong layout file đó dùng (tính cả `<include>`) | ERROR | `NullPointerException` khi chạy: lỗi khó nhất vì build vẫn qua |
| `case R.id.x` | ERROR | Lỗi build trên AGP 8 |
| `inflate(R.layout.x, null)` / `inflate(R.layout.x, parent)` | WARN | Dòng mất chiều cao / crash |
| Adapter `getView` không có `convertView == null` | WARN | Cuộn chậm |
| Adapter có `View.GONE` mà không có `View.VISIBLE` | WARN | Dòng khác bị ẩn sai khi cuộn |
| Manifest khai báo class của app không còn tồn tại | WARN | Crash / lỗi build |

Không bắt được (cần checklist + chạy thử): logic sai, quên `notifyDataSetChanged()`, sửa view ở luồng nền, màu chỉ gán 1 nhánh `if`, sai key HashMap (`"tvCont"`), API trả khác mong đợi.
Nếu báo nhầm (một id lấy từ layout khác qua `LayoutInflater` riêng…) thì xem lại dòng đó; tool chỉ đọc text nên không hiểu hết code.

> Mang file vào VDI hay thêm vào repo của khách: theo quy định của khách / hỏi lead. Tool chỉ đọc file trên máy, không gửi gì ra ngoài.

---

## 3. Checklist

### 3.1 Thêm 1 danh sách (ListView + adapter)

| # | Việc | Ở đâu | Tool soát? |
|---|---|---|---|
| 1 | Layout 1 dòng, đặt id cho từng view | `res/layout/item_xxx.xml` | id dùng sai → có |
| 2 | Có nút / checkbox trong dòng → `android:descendantFocusability="blocksDescendants"` ở layout gốc của dòng | `item_xxx.xml` | không |
| 3 | Adapter: `extends HashMapListAdapter` (hoặc viết tay đủ 4 hàm + `convertView`) | `XxxAdapter.java` | một phần |
| 4 | `bind`: mọi `if` đổi màu / ẩn hiện đều có nhánh ngược lại | `bind` / `getView` | một phần (GONE/VISIBLE) |
| 5 | Activity: `ListView` có trong layout màn, `setAdapter(adapter)` | `onCreate` | id → có |
| 6 | Dữ liệu về: `rows.clear(); rows.addAll(...)` rồi `adapter.notifyDataSetChanged()` **trên luồng giao diện** (trong callback `HttpTask`) | callback | không |
| 7 | Bấm dòng: `setOnItemClickListener`, lấy `rows.get(position)` | Activity | không |
| 8 | Danh sách rỗng: hiện chữ "Không có dữ liệu" | Activity | không |

### 3.2 Thêm 1 màn hình (Activity)

| # | Việc | Tool soát? |
|---|---|---|
| 1 | Class `extends AppCompatActivity`, `setContentView(R.layout.activity_xxx)` | layout → có |
| 2 | Khai báo `<activity android:name=".xxx.XxxActivity" />` trong `AndroidManifest.xml` | **có** |
| 3 | View vào HashMap: key **trùng id XML** (`viewMap.put("tvCount", findViewById(R.id.tvCount))`) | id → có, key → không |
| 4 | Nút: `setOnClickListener(this)` + nhánh `if (id == R.id.btnX)` trong `onClick` (không `switch`) | `switch` → có |
| 5 | Nhận tham số: `getIntent().getStringExtra(EXTRA_…)`; bên gọi `putExtra` cùng hằng số | không |
| 6 | Callback API: đầu callback kiểm tra `isFinishing() || isDestroyed()` | không |
| 7 | Nút mở màn từ màn trước (`startActivity(new Intent(this, XxxActivity.class))`) | không |

### 3.3 Thêm 1 lần gọi API (HttpTask)

| # | Việc |
|---|---|
| 1 | Đường dẫn đúng với backend (so với Nexacro: `svc::…do` → `api/…`) |
| 2 | `onSuccess`: parse trong `try / catch (JSONException)`; key đúng tên trường JSON |
| 3 | `onError`: `httpCode == 0` = mất mạng; 409 = dữ liệu đã đổi → tải lại, không gửi lại |
| 4 | Nút gửi: `setEnabled(false)` khi gửi, bật lại khi có kết quả |
| 5 | POST có `version` nếu API yêu cầu (chống ghi đè) |

### 3.4 Trước khi mang sang máy remote build APK

| # | Việc |
|---|---|
| 1 | `devcheck\run.bat`: **0 ERROR**, đọc hết WARN, quy tắc PASS |
| 2 | Đối chiếu checklist 3.1–3.3 cho phần vừa làm |
| 3 | Ghi lại sẽ thử gì trên máy thật (mở màn, bấm từng nút, danh sách rỗng, mất mạng, cuộn dài) |
| 4 | Build ở remote lỗi → sửa → chạy lại `run.bat` trước khi build tiếp (mỗi vòng remote tốn thời gian) |
