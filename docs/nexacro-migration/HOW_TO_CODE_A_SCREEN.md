# Công thức: tự migrate 1 màn Nexacro → Android (kiểu code của khách)

> 📚 [Mục lục tài liệu](README.md) · Lộ trình: [2 tuần](LEARNING_PLAN_2_WEEKS.md)

Mục tiêu: **tự code theo file này, không cần hỏi AI**. Làm lần lượt 8 bước. Mỗi bước có tiêu chí "xong khi"; chưa đạt thì chưa sang bước sau.
AI chỉ dùng ở bước 8 để review (nếu được phép, xem bước 8).

| Bước | Việc | Ra cái gì |
|---|---|---|
| 1 | Đọc form Nexacro, điền phiếu phân tích | Bảng: Dataset, transaction, event, quy tắc |
| 2 | Truy logic backend cũ, chốt API trong project REST API | Bản đồ: transaction → Controller → Service → SQL → API REST |
| 3 | Đặt tên file theo quy ước | Danh sách file sẽ tạo |
| 4 | Copy khung code, điền vào | Layout, adapter, Activity, manifest |
| 5 | Đặt quy tắc đúng chỗ | Quy tắc ở server / `rules/` / giao diện |
| 6 | Tự kiểm: `devcheck\run.bat` + checklist | 0 ERROR, checklist đủ |
| 7 | Build ở máy remote, thử trên máy thật | Kịch bản thử đã chạy hết |
| 8 | Nhờ AI review | Danh sách góp ý đã xử lý |

Kẹt ở đâu thì tra bảng [cuối file](#kẹt-thì-tra-ở-đâu).

---

## Bước 1: Phân tích form Nexacro

Mở file `.xfdl`, điền phiếu dưới (copy vào ghi chú cá nhân). Đừng bỏ qua phần `<Script>`: nhiều quy tắc **chỉ nằm ở script**, migrate mà chỉ đọc server sẽ sót (bài học R1, R3, R5, R9, R10 ở [nexacro-sample/disposal/README.md](../../nexacro-sample/disposal/README.md) mục 7).

```
Form: frm_xxx.xfdl          Mở từ: (form nào, this.go / popup)    Tham số nhận: (gv_xxx / arguments)

[Dataset]       tên         | cột chính            | dùng làm gì (điều kiện / kết quả / combo)
                ds_search   | keyword              | điều kiện tìm
                ds_list     | code, name, qty      | kết quả, bind vào grd_list

[Transaction]   svcID       | URL (svc::…do)       | in → out              | callback xử lý gì
                search      | xxx/selectList.do    | ds_search → ds_list   | đếm dòng, báo rỗng

[Event]         component   | event                | làm gì
                btn_search  | onclick              | fn_search
                grd_list    | oncellclick          | mở frm_xxx_detail, truyền code

[Quy tắc trong script]  (tìm: if, alert, confirm, return false, gfn_, enable, visible, cssclass)
                R?  | nội dung                      | dòng / hàm
                    | "từ ngày > đến ngày → báo lỗi"  | fn_search

[Hiển thị theo dữ liệu]  cssclass="expr:…", visible, enable theo trạng thái / quyền
```

Tra khái niệm Nexacro → Android: [NEXACRO_TO_ANDROID.md](NEXACRO_TO_ANDROID.md). Ví dụ đã điền sẵn cho cả luồng hủy hàng: [nexacro-sample/disposal/README.md](../../nexacro-sample/disposal/README.md) mục 2 và 7.

**Xong khi:** mọi `transaction`, mọi event, mọi `if` có `alert / confirm / return` trong script đều có dòng trong phiếu.

---

## Bước 2: Truy logic backend cũ → chốt API trong project REST API

Luồng thật của dự án: **form Nexacro** gọi **backend cũ** (`*.do`, X-API) → cần đưa logic đó sang **project REST API** (JSON) → **app Android** gọi REST API.
Làm cho **từng `transaction`** trong phiếu bước 1:

| # | Làm | Tìm thế nào (IntelliJ: `Ctrl+Shift+F` tìm trong cả project) |
|---|---|---|
| 1 | Lấy URL trong form | `this.transaction("search", "svc::stock/selectList.do", ...)` → URL là `stock/selectList.do` |
| 2 | Tìm Controller ở **backend cũ** | Tìm phần cuối `selectList.do` (đường dẫn hay bị tách: `@RequestMapping("/stock")` ở class + `"/selectList.do"` ở hàm) |
| 3 | Đi tiếp Controller → Service → DAO / Mapper | `Ctrl+B` (hoặc Ctrl + click) vào từng hàm được gọi |
| 4 | Đọc **SQL** (thường trong file Mapper `.xml` của MyBatis, tìm theo id câu SQL) | Đây là logic chính: bảng nào, điều kiện gì, cập nhật gì. Ghi lại cả các `if` / `throw` trong Service |
| 5 | Xem dataset vào / ra | Controller đọc `ds_search` cột nào, trả `ds_list` cột nào → đó là tham số / key JSON sẽ dùng |
| 6 | Tìm trong **project REST API** đã có API tương đương chưa | Tìm theo **tên bảng** hoặc **id câu SQL** vừa thấy. Có rồi thì dùng lại; chưa có thì thêm (Controller → Service → Mapper, chép SQL / logic sang) |
| 7 | Chạy thử API REST bằng `curl` / trình duyệt, so với kết quả màn Nexacro cùng điều kiện | Cùng dữ liệu vào → phải cùng dữ liệu ra |

Ghi lại thành bảng (1 dòng = 1 transaction), đây là "bản đồ" để code bước 4 và để reviewer kiểm tra:

```
svcID   | URL cũ (.do)          | Controller.hàm → Service.hàm → SQL id (bảng)                         | API REST                 | Có sẵn?
search  | stock/selectList.do   | StockController.selectList → StockService.getList → selectStockList (TB_STOCK) | GET api/stocks?keyword=  | có
save    | stock/save.do         | StockController.save → StockService.save → updateStock (TB_STOCK)    | POST api/stocks/{code}   | thêm mới
```

Mỗi API REST ghi rõ để code Android không phải đoán:

```
API                      | method | gửi                       | nhận (key JSON)          | lỗi có thể
api/stocks?keyword=      | GET    | –                         | [ {code, name, qty} ]    | 400 điều kiện sai
api/stocks/{code}        | POST   | {version, qty, remark}    | chi tiết mới             | 409 dữ liệu đã đổi
```

Lưu ý:
- **Quy tắc nằm rải 3 nơi**: script form (bước 1), Service backend cũ (`if` / `throw`), và SQL (`WHERE`, `UPDATE ... WHERE status = ...`). Sót chỗ nào là API mới chạy khác bản cũ.
- Thêm API vào project REST API là **sửa code backend dùng chung**: hỏi lead ai làm phần này, đặt tên / cấu trúc theo các API có sẵn trong project đó.
- Chạy project REST API trên máy: [restapi-demo/README.md](../../restapi-demo/README.md) (tập ở nhà), cách chọn môi trường `resources-local`.
- Server chỉ có X-API mà chưa có REST: xem [NEXACRO_XAPI_TO_JSON.md](NEXACRO_XAPI_TO_JSON.md). Mẫu đầy đủ của luồng hủy hàng: [task-17-18-disposal/README.md](../task-17-18-disposal/README.md) (mục API).

**Xong khi:** mỗi transaction có 1 dòng trong bản đồ; mỗi API REST đã gọi thử được (hoặc đã có người nhận làm), có response mẫu, biết mã lỗi cần xử lý (thường: 0 mất mạng, 400, 403, 409).

---

## Bước 3: Đặt tên file

| Loại | Quy ước | Ví dụ (màn tra tồn kho) |
|---|---|---|
| Màn danh sách | `XxxListActivity.java` | `StockListActivity.java` |
| Màn chi tiết | `XxxDetailActivity.java` | `StockDetailActivity.java` |
| Layout màn | `activity_xxx_list.xml` | `activity_stock_list.xml` |
| Layout 1 dòng | `item_xxx.xml` | `item_stock.xml` |
| Adapter | `XxxAdapter.java` | `StockAdapter.java` |
| Quy tắc | `rules/XxxRules.java` | `rules/StockRules.java` |
| id trong XML | `tv…` chữ, `btn…` nút, `edt…` ô nhập, `lv…` ListView, `txt…` ô trong dòng | `tvCount`, `btnSearch`, `edtKeyword`, `lvList`, `txtName` |

Key trong `viewMap` **trùng id XML** (`viewMap.put("tvCount", findViewById(R.id.tvCount))`): sai key là `NullPointerException` mà tool không bắt được.
Dự án của khách có quy ước riêng thì theo dự án (xem 2–3 màn có sẵn trước khi đặt tên).

**Xong khi:** có danh sách file sẽ tạo / sửa (kể cả `AndroidManifest.xml` và màn gọi tới màn mới).

---

## Bước 4: Khung code (copy rồi điền)

Thay `Xxx` / `xxx`, key JSON, id cho đúng phiếu bước 1–2. Chỗ `TODO` là chỗ phải điền.
Khung dưới **giống code mẫu đã chạy** trong repo: `plain/PlainDisposalListActivity.java`, `PlainDisposalListAdapter.java`, `PlainDisposalDetailActivity.java`. Không chắc thì mở file mẫu đối chiếu.

### 4a. Layout màn: `res/layout/activity_xxx_list.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="12dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal">

        <EditText
            android:id="@+id/edtKeyword"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="Từ khoá"
            android:imeOptions="actionSearch"
            android:inputType="text" />

        <Button
            android:id="@+id/btnSearch"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Tìm" />
    </LinearLayout>

    <TextView
        android:id="@+id/tvCount"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:paddingTop="8dp"
        android:paddingBottom="8dp" />

    <ListView
        android:id="@+id/lvList"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />
</LinearLayout>
```

### 4b. Layout 1 dòng: `res/layout/item_xxx.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Có Button / CheckBox trong dòng: thêm android:descendantFocusability="blocksDescendants" vào LinearLayout gốc -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingTop="8dp"
    android:paddingBottom="8dp">

    <TextView
        android:id="@+id/txtName"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="15sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/txtSub"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="13sp" />
</LinearLayout>
```

### 4c. Adapter: `XxxAdapter.java`

```java
package com.example.andemo.xxx;   // TODO package

import android.content.Context;
import android.graphics.Color;

import com.example.andemo.R;
import com.example.andemo.plain.HashMapListAdapter;
import com.example.andemo.plain.JsonRows;

import java.util.HashMap;
import java.util.List;

public class XxxAdapter extends HashMapListAdapter {

    public XxxAdapter(Context context, List<HashMap<String, String>> rows) {
        super(context, rows, R.layout.item_xxx);
    }

    @Override
    protected void bind(RowViews v, HashMap<String, String> row, int position) {
        v.text(R.id.txtName).setText(row.get("name"));                         // TODO key JSON
        v.text(R.id.txtSub).setText(row.get("code") + " · " + JsonRows.won(row.get("price")));

        // Màu / ẩn hiện theo dữ liệu (≈ cssclass="expr:…"): LUÔN gán cả 2 nhánh
        boolean warn = "0".equals(row.get("qty"));
        v.text(R.id.txtSub).setTextColor(warn ? Color.RED : Color.DKGRAY);
    }
}
```

### 4d. Màn danh sách (GET): `XxxListActivity.java`

```java
package com.example.andemo.xxx;   // TODO package

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.andemo.R;
import com.example.andemo.plain.HttpTask;
import com.example.andemo.plain.JsonRows;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class XxxListActivity extends AppCompatActivity implements View.OnClickListener {

    private final HashMap<String, View> viewMap = new HashMap<>();
    private final List<HashMap<String, String>> rows = new ArrayList<>();   // ≈ ds_list
    private XxxAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xxx_list);
        setTitle("TODO tiêu đề");

        // 1) View vào HashMap: key = id
        viewMap.put("edtKeyword", findViewById(R.id.edtKeyword));
        viewMap.put("btnSearch", findViewById(R.id.btnSearch));
        viewMap.put("tvCount", findViewById(R.id.tvCount));
        viewMap.put("lvList", findViewById(R.id.lvList));

        // 2) Nút dùng chung 1 listener (onClick bên dưới)
        viewMap.get("btnSearch").setOnClickListener(this);

        // 3) ListView + adapter (≈ Grid bind ds_list)
        adapter = new XxxAdapter(this, rows);
        ListView lv = (ListView) viewMap.get("lvList");
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((parent, view, position, id) -> openDetail(rows.get(position)));

        search();   // ≈ form_onload
    }

    /** if / else theo id, KHÔNG switch (AGP 8: R.id không phải hằng số) */
    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnSearch) {
            search();
        }
    }

    /** ≈ fn_search: this.transaction("search", …) */
    private void search() {
        String keyword = ((EditText) viewMap.get("edtKeyword")).getText().toString().trim();
        ((TextView) viewMap.get("tvCount")).setText("Đang tải…");
        viewMap.get("btnSearch").setEnabled(false);

        HttpTask.get(this, "api/xxx?keyword=" + Uri.encode(keyword), new HttpTask.Callback() {   // TODO API
            @Override
            public void onSuccess(String body) {          // ≈ fn_callback, errorCode >= 0
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                viewMap.get("btnSearch").setEnabled(true);
                try {
                    List<HashMap<String, String>> result = JsonRows.rows(body);
                    rows.clear();
                    rows.addAll(result);
                    adapter.notifyDataSetChanged();
                    ((TextView) viewMap.get("tvCount")).setText(
                            rows.isEmpty() ? "Không có dữ liệu" : rows.size() + " dòng");
                } catch (JSONException e) {
                    onError(200, "Dữ liệu server không đúng định dạng");
                }
            }

            @Override
            public void onError(int httpCode, String message) {   // ≈ errorCode < 0
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                viewMap.get("btnSearch").setEnabled(true);
                ((TextView) viewMap.get("tvCount")).setText("");
                Toast.makeText(XxxListActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** ≈ grd_list_oncellclick → this.go("frm::frm_xxx_detail.xfdl") */
    private void openDetail(HashMap<String, String> row) {
        Intent intent = new Intent(this, XxxDetailActivity.class);   // TODO màn chi tiết
        intent.putExtra(XxxDetailActivity.EXTRA_CODE, row.get("code"));
        startActivity(intent);
    }
}
```

Chưa làm màn chi tiết thì tạm bỏ `openDetail` và dòng `setOnItemClickListener`.

### 4e. Gửi dữ liệu (POST), dùng trong màn chi tiết / đăng ký

```java
public static final String EXTRA_CODE = "code";   // bên gọi putExtra cùng hằng số này
// trong onCreate: code = getIntent().getStringExtra(EXTRA_CODE);

private void save() {
    // 1) Kiểm tra trước khi gửi (quy tắc ở rules/, xem bước 5)
    String remark = ((EditText) viewMap.get("edtRemark")).getText().toString().trim();
    String error = XxxRules.remarkError(remark);                      // TODO quy tắc
    if (error != null) {
        ((EditText) viewMap.get("edtRemark")).setError(error);
        return;
    }
    // 2) Body JSON
    JSONObject body = new JSONObject();
    try {
        body.put("version", version);                                 // chống ghi đè (409)
        body.put("remark", remark);
    } catch (JSONException e) {
        return;
    }
    // 3) Chống bấm 2 lần
    viewMap.get("btnSave").setEnabled(false);
    HttpTask.post(this, "api/xxx/" + code, body, new HttpTask.Callback() {   // TODO API
        @Override
        public void onSuccess(String responseBody) {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            viewMap.get("btnSave").setEnabled(true);
            Toast.makeText(XxxDetailActivity.this, "Đã lưu", Toast.LENGTH_SHORT).show();
            // TODO: hiển thị lại từ responseBody, hoặc finish() để về danh sách (onResume tải lại)
        }

        @Override
        public void onError(int httpCode, String message) {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            viewMap.get("btnSave").setEnabled(true);
            // 0 = mất mạng: KHÔNG tự gửi lại (không biết server đã lưu chưa) → tải lại để xem
            // 409 = người khác đã sửa → tải lại
            new AlertDialog.Builder(XxxDetailActivity.this)
                    .setTitle(httpCode == 0 ? "Mất kết nối" : "Không lưu được")
                    .setMessage(message)
                    .setPositiveButton("Tải lại", (d, w) -> load())
                    .show();
        }
    });
}
```

Hỏi lại trước khi làm (≈ `this.confirm`): xem `askConfirm` trong `plain/PlainDisposalDetailActivity.java`. Ô nhập trong hộp thoại (≈ popup nhập lý do): xem `askReason` ở cùng file.

### 4f. Khai báo + nút mở màn

```xml
<!-- AndroidManifest.xml, trong <application> -->
<activity android:name=".xxx.XxxListActivity" />
<activity android:name=".xxx.XxxDetailActivity" />
```

```java
// Màn gọi tới (ví dụ MainActivity): ≈ this.go(...)
startActivity(new Intent(this, XxxListActivity.class));
```

### 4g. Quy tắc + test bằng main

```java
// app/src/main/java/com/example/andemo/rules/XxxRules.java: CHỈ import java.*
public final class XxxRules {
    public static final int REMARK_MAX = 100;
    private XxxRules() { }

    /** null = hợp lệ */
    public static String remarkError(String remark) {
        return remark != null && remark.length() > REMARK_MAX ? "Tối đa " + REMARK_MAX + " ký tự" : null;
    }
}
```

Thêm test vào `devcheck/` theo [WORK_WITHOUT_BUILD.md](WORK_WITHOUT_BUILD.md) mục 6 (nhớ thêm file mới vào dòng `javac` của `run.bat`).

**Xong khi:** mọi `TODO` đã điền; mỗi dòng trong phiếu bước 1 có chỗ tương ứng trong code.

---

## Bước 5: Quy tắc đặt ở đâu

| Loại quy tắc (trong script Nexacro) | Đặt ở | Ví dụ |
|---|---|---|
| Đúng / sai của **dữ liệu** (độ dài cột, ngày hợp lệ, nghiệp vụ) | **Server** (bắt buộc) + `rules/` để báo sớm | R5 ghi chú ≤ 100, R9 chỉ xác nhận phiếu hôm nay |
| Chỉ là **hỏi lại / cảnh báo** người dùng | `rules/` (tính) + Activity (hiện dialog) | R10 giá trị lớn hỏi lần 2, R3 thiếu tồn |
| **Hiển thị**: màu, ẩn / hiện, bật / tắt nút | Adapter `bind` / Activity; nút theo quyền / trạng thái thì để server trả `actions` | `cssclass="expr:…"`, `fn_setButtons` |

Không chắc quy tắc thuộc loại nào → coi là loại 1 (server) và hỏi BA / lead. Ví dụ đủ 13 quy tắc: [nexacro-sample/disposal/README.md](../../nexacro-sample/disposal/README.md) mục 7.

**Xong khi:** mỗi dòng "Quy tắc trong script" ở phiếu bước 1 đã ghi rõ nằm ở đâu.

---

## Bước 6: Tự kiểm

1. `devcheck\run.bat` (từ thư mục `android`): **0 ERROR**, đọc hết WARN, test quy tắc PASS. Chi tiết: [WORK_WITHOUT_BUILD.md](WORK_WITHOUT_BUILD.md).
2. Dò checklist [WORK_WITHOUT_BUILD.md mục 3](WORK_WITHOUT_BUILD.md#3-checklist) (danh sách / màn hình / gọi API).
3. Đọc lại diff 1 lượt với 4 câu hỏi:
   - Callback nào thiếu `isFinishing() || isDestroyed()`?
   - Nút gửi nào chưa `setEnabled(false)` khi gửi và bật lại ở **cả** `onSuccess` lẫn `onError`?
   - `if` đổi màu / ẩn hiện nào thiếu nhánh ngược lại?
   - Key HashMap nào gõ tay mà không trùng id / key JSON?

**Xong khi:** cả 3 mục trên sạch.

---

## Bước 7: Build ở máy remote + thử trên máy thật

Build lỗi → sửa → chạy lại bước 6 trước khi build lại. Cài APK, thử theo kịch bản (ghi kết quả từng dòng):

| # | Thử | Mong đợi |
|---|---|---|
| 1 | Mở màn | Không crash, tự tải dữ liệu |
| 2 | Từng nút | Đúng việc trong phiếu bước 1 |
| 3 | Không có dữ liệu | Hiện "Không có dữ liệu", không crash |
| 4 | Nhiều dòng, cuộn lên xuống | Màu / ẩn hiện không "dính" sai dòng |
| 5 | Bấm nút gửi 2 lần thật nhanh | Chỉ gửi 1 lần |
| 6 | Tắt mạng rồi bấm | Báo mất kết nối, không crash |
| 7 | Mở màn rồi bấm Back ngay khi đang tải | Không crash |
| 8 | Từng quy tắc ở bước 5 | Báo đúng nội dung, đúng lúc |
| 9 | Xoay màn hình (nếu app cho xoay) | Không crash, dữ liệu còn / tải lại |

Lỗi khi chạy: Logcat lọc `AndroidRuntime` (crash) hoặc `tag:HttpTask` (gọi API).

---

## Bước 8: Nhờ AI review

> **Trước tiên:** code / dữ liệu / log của khách **không dán vào công cụ AI bên ngoài** nếu khách hoặc công ty chưa cho phép.
> Chưa được phép thì tự review bằng bước 6 + kịch bản bước 7, hoặc nhờ đồng nghiệp; hay viết lại phần nghi ngờ thành ví dụ trong repo luyện tập này (không chứa thông tin của khách) rồi mới hỏi.

Được phép thì gửi **đủ ngữ cảnh 1 lần** để AI không phải hỏi lại:

```
Review code Android Java migrate từ Nexacro 17. Kiểu code của dự án: HttpTask (thread + HttpURLConnection + org.json),
view trong HashMap + 1 OnClickListener chung, ListView + adapter kế thừa HashMapListAdapter. AGP 8 (không switch R.id).
minSdk 24 (không java.time).

1. Phiếu phân tích form (bước 1): <dán>
2. Bảng API + response mẫu (bước 2, đã xoá dữ liệu thật): <dán>
3. Code: <dán các file mới / diff>
4. Kết quả devcheck\run.bat: <dán>
5. Kịch bản thử đã chạy và kết quả (bước 7): <dán>

Hãy kiểm tra:
- Có sót event / transaction / quy tắc nào trong phiếu bước 1 không
- Lỗi luồng: sửa view ở luồng nền, thiếu isFinishing, bấm 2 lần, mất mạng khi POST
- Adapter: view dùng lại (thiếu nhánh else), sai key HashMap
- Quy tắc đặt sai chỗ (bảng bước 5)
Chỉ ra file:dòng, mức độ (lỗi / nên sửa / gợi ý), và cách sửa.
```

Nhận góp ý: sửa phần **lỗi** trước, chạy lại bước 6 → 7; phần "gợi ý" đối chiếu với quy ước dự án rồi mới làm.

---

## Kẹt thì tra ở đâu

| Kẹt ở | Tra |
|---|---|
| Khái niệm Nexacro này trên Android là gì (Dataset, Grid, Combo, popup, `gfn_`…) | [NEXACRO_TO_ANDROID.md](NEXACRO_TO_ANDROID.md) |
| `HttpTask`, HashMap view, adapter, lỗi luồng | [PLAIN_JAVA_STYLE.md](PLAIN_JAVA_STYLE.md) |
| Code đỏ, không build / chạy được trong VDI | [WORK_WITHOUT_BUILD.md](WORK_WITHOUT_BUILD.md) |
| Server chỉ có X-API (XML), chưa có JSON | [NEXACRO_XAPI_TO_JSON.md](NEXACRO_XAPI_TO_JSON.md) |
| Nghiệp vụ hủy hàng (상태, 수불, 마감) | [task-17-18-disposal/README.md](../task-17-18-disposal/README.md) |
| Cần 1 ví dụ hoàn chỉnh Nexacro ↔ Android | [nexacro-sample/disposal/README.md](../../nexacro-sample/disposal/README.md) + `android/.../plain/` |
| Quy trình migrate cả dự án, Definition of Done | [MIGRATION_PLAN.md](MIGRATION_PLAN.md) mục A6 |
