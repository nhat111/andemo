# Chuyển response X-API (XML Dataset) sang JSON cho Android

> Nhánh: `claude/android-nexacro-migration`. Đi kèm [NEXACRO_MIGRATION_LAB.md](NEXACRO_MIGRATION_LAB.md), [NEXACRO_TO_ANDROID.md](NEXACRO_TO_ANDROID.md).
> Cập nhật: 2026-09-29. Demo chạy được và có test (backend 48/48).

---

## 1. Vấn đề

Server Nexacro hiện có (các URL `*.do`) dùng **X-API**, gửi / nhận **XML Dataset** (hoặc SSV). App Android dùng Retrofit + Gson, hợp với **JSON**. Có 3 cách nối 2 bên:

| Cách | Làm gì | Sửa server cũ? | Khi nào dùng |
|---|---|---|---|
| **A. Cổng chuyển đổi** (demo trong repo) | Backend mới nhận JSON → đổi sang XML → gọi `*.do` cũ → đổi XML trả về → JSON | **Không** | Cần chạy nhanh, server cũ không được đụng vào, hoặc do team khác quản lý |
| **B. Controller JSON trong server cũ** | Thêm `@RestController` cạnh controller X-API, gọi lại **service cũ**; dùng `XapiJsonConverter` nếu service trả `PlatformData` | Có (thêm, không sửa cái cũ) | Hướng lâu dài, khuyên dùng. Bỏ được 1 bước mạng + chuyển đổi |
| **C. X-API / Nexacro trả JSON sẵn** | Một số phiên bản Nexacro / X-API có hỗ trợ định dạng JSON | Tuỳ phiên bản | **Mình chưa kiểm chứng được**; kiểm tra tài liệu X-API đúng bản dự án trước khi dựa vào |

Định dạng JSON của cách A và B **giống nhau**, nên app Android viết 1 lần, sau này chuyển từ A sang B chỉ cần đổi URL.

---

## 2. Định dạng

### XML Dataset (X-API)

```xml
<Root xmlns="http://www.nexacroplatform.com/platform/dataset">
  <Parameters>
    <Parameter id="ErrorCode" type="int">0</Parameter>
    <Parameter id="ErrorMsg" type="string">SUCC</Parameter>
  </Parameters>
  <Dataset id="ds_list">
    <ColumnInfo>
      <Column id="barcode" type="string" size="256"/>
      <Column id="stockQuantity" type="int" size="10"/>
    </ColumnInfo>
    <Rows>
      <Row>
        <Col id="barcode">8851993123456</Col>
        <Col id="stockQuantity">40</Col>
      </Row>
    </Rows>
  </Dataset>
</Root>
```

### JSON tương ứng

```json
{
  "errorCode": 0,
  "errorMsg": "SUCC",
  "params": {},
  "datasets": {
    "ds_list": [
      { "barcode": "8851993123456", "stockQuantity": 40 }
    ]
  }
}
```

### Quy tắc chuyển đổi

| XML Dataset | JSON |
|---|---|
| `Parameter ErrorCode` / `ErrorMsg` | `errorCode` / `errorMsg` (tách riêng cho dễ kiểm tra) |
| Các `Parameter` khác | `params` |
| `Dataset id="ds_x"` | `datasets.ds_x` = mảng, **mỗi dòng 1 object** |
| Cột `int` | số nguyên JSON |
| Cột `bigdecimal` / `float` | số thập phân JSON |
| Cột `string`, `date` (`yyyyMMdd`), `datetime` | chuỗi, giữ nguyên dạng |
| `<Col>` rỗng hoặc không có | không có key (null) |
| `<Row type="insert/update/delete">` | `"_rowType": "insert"` … |
| `<OrgRow>` (giá trị gốc của dòng update) | `"_orgRow": { ... }` |

Chiều ngược lại (JSON từ app → XML gửi server cũ): cột và kiểu cột **suy ra từ dữ liệu** (số nguyên → `int`, số thập phân → `bigdecimal`, còn lại `string`).

Chưa hỗ trợ trong demo: SSV, binary, `ConstColumn`, cột blob.

---

## 3. Demo chạy được (cách A)

### Thành phần

| File | Vai trò |
|---|---|
| `backend/.../nexacro/NexacroXml.java` | Đọc / ghi XML Dataset (chỉ dùng JDK, chặn XXE) |
| `backend/.../nexacro/NexacroJson.java` | XML ↔ JSON theo quy tắc mục 2 |
| `backend/.../nexacro/LegacyNexacroController.java` | **Giả lập server Nexacro cũ**: `/nexacro/*.do`, nhận và trả XML. Dự án thật thì đây là server X-API có sẵn |
| `backend/.../nexacro/NexacroGatewayController.java` | **Cổng JSON** `/api/nx/**` cho app (cần JWT) |
| `android/.../api/NexacroGatewayApi.java`, `model/NxRequest.java`, `model/NxResponse.java` | Client Android |
| `android/.../migration/NexacroGatewayDemoActivity.java` | Màn demo 4 nút |
| `backend/src/test/.../NexacroGatewayIntegrationTest.java`, `NexacroXmlTest.java` | 12 test |
| `nexacro-sample/XapiJsonConverter.java.txt` | Helper cho cách B (dùng X-API thật) |

```
App Android ──JSON──► /api/nx/product/search ──XML──► /nexacro/product/search.do (X-API cũ)
            ◄──JSON──  (NexacroGatewayController)  ◄──XML──
```

Cấu hình: biến môi trường `NEXACRO_LEGACY_BASE_URL` = địa chỉ server Nexacro thật, ví dụ `http://erp.noibo:8080/app/`. Để trống thì cổng gọi server giả lập ngay trong backend này.

### 4 ví dụ

Chạy backend nhánh này, lấy token:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"user","password":"123456"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
```

**Ví dụ 1: tra cứu, 1 in-dataset → 1 out-dataset**

Nexacro: `transaction("search", "svc::product/search.do", "ds_search=ds_search", "ds_list=ds_list", "", "fn_callback")`
```bash
curl -s -X POST http://localhost:8080/api/nx/product/search \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"datasets":{"ds_search":[{"keyword":"sữa"}]}}'
```
```json
{"errorCode":0,"errorMsg":"SUCC","params":{},
 "datasets":{"ds_list":[{"barcode":"8851993123456","name":"Sữa TH True Milk 1L",
   "description":"Sữa tươi tiệt trùng","imageUrl":"https://picsum.photos/seed/milk/400/400","stockQuantity":40}]}}
```

So sánh: gọi thẳng server cũ bằng XML (như form Nexacro gọi):
```bash
curl -s -X POST http://localhost:8080/nexacro/product/search.do -H 'Content-Type: text/xml; charset=UTF-8' --data-binary @- <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<Root xmlns="http://www.nexacroplatform.com/platform/dataset">
  <Dataset id="ds_search">
    <ColumnInfo><Column id="keyword" type="string" size="100"/></ColumnInfo>
    <Rows><Row><Col id="keyword">sữa</Col></Row></Rows>
  </Dataset>
</Root>
EOF
```

**Ví dụ 2: mã chung, 1 transaction trả nhiều Dataset + Parameter**

Nexacro: out-dataset `"ds_category=ds_category ds_unit=ds_unit"`
```bash
curl -s -X POST http://localhost:8080/api/nx/code/list \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}'
```
```json
{"errorCode":0,"errorMsg":"SUCC","params":{"codeVersion":"20260929"},
 "datasets":{"ds_category":[{"code":"DRINK","name":"Đồ uống"},{"code":"FOOD","name":"Thực phẩm"},{"code":"DAIRY","name":"Sữa"}],
             "ds_unit":[{"code":"EA","name":"Cái"},{"code":"BOX","name":"Hộp"},{"code":"BTL","name":"Chai"}]}}
```

**Ví dụ 3: lưu, gửi dòng thay đổi kèm rowtype (`ds_stock:U`)**

Nexacro: `transaction("save", "svc::stock/save.do", "ds_stock=ds_stock:U", "ds_result=ds_result", ...)`
```bash
curl -s -X POST http://localhost:8080/api/nx/stock/save \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"datasets":{"ds_stock":[
        {"_rowType":"insert","barcode":"8936036020151","qty":10,"note":"Nhập lô A"},
        {"_rowType":"update","barcode":"8936036020151","qty":8,"_orgRow":{"barcode":"8936036020151","qty":5}}]}}'
```
```json
{"errorCode":0,"errorMsg":"SUCC","params":{"savedCount":2},
 "datasets":{"ds_result":[{"barcode":"8936036020151","stockQuantity":38}]}}
```
Tồn Cà phê G7: 25 + 10 (insert) + 3 (update 5 → 8) = 38.

**Ví dụ 4: lỗi nghiệp vụ, `ErrorCode < 0` → HTTP 400**
```bash
curl -s -w '\nHTTP %{http_code}\n' -X POST http://localhost:8080/api/nx/stock/save \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"datasets":{"ds_stock":[{"_rowType":"insert","barcode":"8936036020151","qty":0}]}}'
```
```
{"errorCode":-2,"errorMsg":"Dòng 1: số lượng phải > 0","params":{},"datasets":{}}
HTTP 400
```

| Tình huống | HTTP | `errorCode` |
|---|---|---|
| Thành công | 200 | ≥ 0 |
| Server cũ trả `ErrorCode < 0` | 400 | giá trị server cũ trả |
| Tên service sai định dạng (có `.`, `..`) | 400 | -900 |
| Server cũ trả HTTP lỗi (404, 500…) | 502 | -901 |
| Không kết nối được server cũ | 502 | -902 |
| Chưa đăng nhập | 401 | |

### Trên app Android

Build Variant `pollingDebug` → Run → login `user` → **"Demo X-API → JSON"** → bấm 4 nút.

Code gọi (trích `NexacroGatewayDemoActivity`):
```java
// ~ this.transaction("search", "svc::product/search.do", "ds_search=ds_search", "ds_list=ds_list", "", "fn_callback")
NxRequest req = new NxRequest();
req.row("ds_search").put("keyword", "sữa");

api.call("product/search", req).enqueue(new Callback<NxResponse>() {
    @Override
    public void onResponse(Call<NxResponse> call, Response<NxResponse> response) {
        NxResponse res = response.isSuccessful() && response.body() != null
                ? response.body()
                : NxResponse.fromError(response);              // HTTP 400: đọc errorBody
        if (!res.isSuccess()) {                                 // ~ if (errorCode < 0)
            Toast.makeText(ctx, res.getErrorMsg(), Toast.LENGTH_SHORT).show();
            return;
        }
        List<ProductDto> list = res.dataset("ds_list", ProductDto.class);   // Dataset → List<POJO>
    }
    @Override
    public void onFailure(Call<NxResponse> call, Throwable t) { /* mất mạng */ }
});
```

Gửi dòng có rowtype:
```java
Map<String, Object> row = req.row("ds_stock", "update");
row.put("barcode", "8936036020151");
row.put("qty", 8);
row.put("_orgRow", Map.of("barcode", "8936036020151", "qty", 5));
```

---

## 4. Đưa vào dự án thật

**Cách A (cổng, không sửa server cũ):**
1. Deploy backend này (hoặc copy package `nexacro` sang backend Spring của dự án).
2. Đặt `NEXACRO_LEGACY_BASE_URL` trỏ tới server Nexacro thật.
3. **Xoá hoặc tắt `LegacyNexacroController`** và dòng `permitAll("/nexacro/**")` trong `SecurityConfig`: chúng chỉ để demo.
4. Kiểm tra server cũ xác thực thế nào: nếu dựa vào session / cookie đăng nhập Nexacro thì cổng phải đăng nhập hộ, hoặc truyền thông tin user qua Parameter. Phần này phụ thuộc hệ thống thật, cần xem code.
5. Test từng service bằng curl như mục 3 trước khi viết màn hình Android.

**Cách B (controller JSON trong server cũ):** xem `nexacro-sample/XapiJsonConverter.java.txt`. Service cũ trả `List<VO>` thì trả thẳng, không cần converter.

**Lưu ý chung:**
- Tên cột Dataset (thường VIẾT HOA, ví dụ `PRODUCT_NM`) phải trùng tên field Java để `dataset(id, Class)` tự map. Không trùng thì dùng `@SerializedName("PRODUCT_NM")` trên field.
- Ngày giờ Nexacro là chuỗi `yyyyMMdd` / `yyyyMMddHHmmssSSS`: app tự format khi hiển thị.
- Dataset rất lớn: XML + chuyển đổi tốn bộ nhớ; nên phân trang từ server cũ.

---

## 5. Còn về phía Nexacro (client)

Nếu ý là "form Nexacro nhận JSON thay XML": `transaction()` của Nexacro mặc định làm việc với XML / SSV / binary. Một số phiên bản Nexacro N có hỗ trợ JSON hoặc có thể tự gọi HTTP và parse JSON trong script, nhưng **mình chưa kiểm chứng được trên bản của dự án**. Cách an toàn: giữ form Nexacro nói XML như cũ, chỉ app Android dùng JSON (cách A hoặc B).
