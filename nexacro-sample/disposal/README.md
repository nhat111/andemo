# 폐기 (hủy hàng) bản Nexacro 17 mobile: mã nguồn "cũ" để luyện đọc và migrate

> Đây là **bản mô phỏng hệ thống Nexacro 17 mobile cũ** cho task 17–18, dùng để luyện đúng việc sẽ làm ở dự án thật:
> đọc form Nexacro → rút quy tắc → chuyển sang Android (Java).
> - **Phía server (X-API giả lập): chạy được, có test** (`LegacyDisposalXapiTest`, 5 test).
> - **Phía client (.xfdl, .xjs, .xcss): viết tay theo cú pháp Nexacro 17**, đã đối chiếu với project mẫu chính thức của TOBESOFT và tài liệu (mục 7), **nhưng CHƯA mở thử trong Nexacro Studio**. Mở lên có thể phải chỉnh vài thuộc tính giao diện.
>
> Bản Android đã làm sẵn nằm ở `android/.../disposal/` và `docs/task-17-18-disposal/README.md`: coi như **đáp án**, đừng mở trước khi tự làm bài tập ở mục 5.

---

## 1. Cấu trúc

```
nexacro-sample/disposal/
├─ appvariables.xml           biến toàn cục gv_* (định dạng Nexacro 17)
├─ _resource_/_xcss_/
│  └─ disposal.xcss           màu chữ Grid theo điều kiện (Cell cssclass="expr:…")
├─ lib/
│  └─ common.xjs              thư viện chung gfn_: thông báo, kiểm tra, định dạng, transaction, chuyển form
└─ form/
   ├─ frm_login.xfdl          đăng nhập → session + biến toàn cục gv_*
   ├─ frm_disposal_list.xfdl  폐기조회 (tra cứu)                   Task 17
   ├─ frm_disposal_detail.xfdl 폐기상세 + 확정 / 확정취소 / 취소    Task 17 (+ 18 phía server)
   └─ frm_disposal_reg.xfdl   폐기등록 / 수정 (quét barcode)

backend/.../nexacro/LegacyDisposalController.java   server X-API giả lập (URL *.do, XML Dataset, HttpSession)
backend/src/test/.../LegacyDisposalXapiTest.java    test gọi đúng kiểu form Nexacro
```

Luồng màn hình (mobile: mỗi lúc 1 form, chuyển bằng `gfn_go`, truyền số phiếu qua `gv_disposalNo`):

```
frm_login ──► frm_disposal_list ──(chọn phiếu)──► frm_disposal_detail ──(수정)──► frm_disposal_reg
                    │                                  ▲                               │
                    └──────────(+ 폐기등록)──────────────┼───────────────────────────────┘
                                                       └──────────(lưu xong)───────────┘
```

---

## 2. Mở bằng Nexacro Studio 17

Cách an toàn nhất: **tạo project mới bằng wizard của Studio** (để Studio tự sinh theme, `typedefinition.xml`, `environment.xml`), rồi chép các file ở đây vào và bổ sung cấu hình dưới đây.

**1. Chép file**

| Từ | Vào project |
|---|---|
| `form/*.xfdl` | thư mục `form/` |
| `lib/common.xjs` | thư mục `lib/` |
| `_resource_/_xcss_/disposal.xcss` | `_resource_/_xcss_/` |
| `appvariables.xml` | thay file cùng tên (hoặc thêm 5 biến `gv_*` trong Studio: Project Explorer › AppVariables) |

**2. TypeDefinition › Services** (thêm vào `<Services>` của `typedefinition.xml`, hoặc dùng nút `+` ở User Service):

```xml
<Service prefixid="frm" type="form" cachelevel="session" url="./form/" version="0" communicationversion="0" include_subdir="false"/>
<Service prefixid="lib" type="form" cachelevel="session" url="./lib/" version="0" communicationversion="0" include_subdir="false"/>
<Service prefixid="xcssrc" type="resource" url="./_resource_/_xcss_/" include_subdir="false"/>
<!-- Server: backend nhánh này. Máy ảo Android: http://10.0.2.2:8080/nexacro/ -->
<Service prefixid="svc" type="JSP" cachelevel="none" url="http://localhost:8080/nexacro/" version="0" communicationversion="0" include_subdir="false"/>
```

Loại service lấy theo tài liệu TypeDefinition của Nexacro 17: `form` quản lý cả `*.xfdl` và `*.xjs`; `JSP` là service gọi server (dùng được cho URL `*.do` của Spring).

**3. Application (`.xadl`)**: form đầu tiên là màn login, và nạp file xcss:

```xml
<MainFrame id="mainframe" showtitlebar="false" showstatusbar="false" width="480" height="800">
  <ChildFrame id="ChildFrame00" formurl="frm::frm_login.xfdl" showtitlebar="false"/>
</MainFrame>
...
<Style url="xcssrc::disposal.xcss"/>
```

**4. Environment**: màn hình mobile, ví dụ `<Screen id="Screen_M" type="phone"/>` (hoặc giữ Screen wizard đã tạo cho mobile).

Chuyển form dùng `this.go("frm::…")` trong `gfn_go` (có trong project mẫu Nexacro 17). Nếu dự án dùng cách khác (`set_formurl`, menu, frameset…) thì sửa 1 chỗ trong `common.xjs`.

---

## 3. Danh sách transaction (hợp đồng với server)

Mọi URL: `POST {svc}/…`, body XML Dataset. Lỗi: `ErrorCode < 0`, `ErrorMsg` (tiếng Hàn + tiếng Việt).

| svcID (form) | URL | Gửi lên (in-dataset / args) | Nhận về |
|---|---|---|---|
| `login` | `common/login.do` | `ds_login` (USER_ID, PASSWORD) | `ds_user` (USER_ID, USER_ROLE, STORE_CD, BIZ_DT) + **cookie session** |
| `code_ds_*` | `common/selectCode.do` | args `GRP_CD` = `DISP_RSN` \| `DISP_STAT` | `ds_code` (CD, CD_NM) |
| `search` (list) | `disposal/selectList.do` | `ds_cond` (STAT_CD, FROM_DT, TO_DT) | `ds_list` |
| `search` / `load` | `disposal/selectDetail.do` | args `DISPOSAL_NO` | `ds_master` (1 dòng), `ds_detail` |
| `item` | `disposal/selectItem.do` | args `ITEM_CD` | `ds_item` (tồn, tồn khả dụng, giá) |
| `save` | `disposal/save.do` | `ds_master` (DISPOSAL_NO, REMARK, VER) + **`ds_detail:U`** (chỉ dòng insert / update / delete) | `ds_master`, `ds_detail` sau khi lưu |
| `confirm` | `disposal/confirm.do` | args `DISPOSAL_NO`, `VER` | `ds_master`, `ds_detail` |
| `cfmCancel` | `disposal/cancelConfirm.do` | args `DISPOSAL_NO`, `VER`, `RSN` | như trên |
| `cancel` | `disposal/cancel.do` | args `DISPOSAL_NO`, `VER`, `RSN` | như trên |

| ErrorCode | Nghĩa |
|---|---|
| -99 | Hết phiên (chưa login / cookie mất) → `gfn_callback` đưa về màn login |
| -1 | Dữ liệu không hợp lệ / lỗi chung |
| -2 | Người khác đã sửa phiếu (VER khác) |
| -3 | Không có quyền (không phải 점장) |
| -4 | 영업일자 đã 마감 |
| -5 | Thiếu 가용재고 |

Quy ước dữ liệu (kiểu hệ thống Hàn Quốc): cột CHỮ_HOA, ngày `yyyyMMdd`, giờ `yyyyMMddHHmmss` (giờ Hàn), trạng thái `10 / 20 / 90`, lý do `01 / 02 / 03 / 04 / 05 / 99` (bảng mã chung `DISP_RSN`), cờ `Y / N`.

---

## 4. Chạy thử phía server không cần Studio

Chạy backend nhánh này, rồi gọi bằng curl **y như runtime Nexacro gọi**: XML + giữ cookie.

```bash
B=http://localhost:8080/nexacro

# 1. Login: -c lưu cookie JSESSIONID (runtime Nexacro tự làm việc này)
curl -s -c cj.txt -X POST $B/common/login.do -H 'Content-Type: text/xml; charset=UTF-8' --data-binary '
<Root xmlns="http://www.nexacroplatform.com/platform/dataset">
  <Dataset id="ds_login">
    <ColumnInfo><Column id="USER_ID" type="STRING" size="20"/><Column id="PASSWORD" type="STRING" size="100"/></ColumnInfo>
    <Rows><Row><Col id="USER_ID">admin</Col><Col id="PASSWORD">123456</Col></Row></Rows>
  </Dataset>
</Root>'

# 2. Tra cứu phiếu đã xác nhận: -b gửi cookie
curl -s -b cj.txt -X POST $B/disposal/selectList.do -H 'Content-Type: text/xml; charset=UTF-8' --data-binary '
<Root xmlns="http://www.nexacroplatform.com/platform/dataset">
  <Dataset id="ds_cond">
    <ColumnInfo><Column id="STAT_CD" type="STRING" size="2"/></ColumnInfo>
    <Rows><Row><Col id="STAT_CD">20</Col></Row></Rows>
  </Dataset>
</Root>'

# 3. Không gửi cookie → ErrorCode -99
curl -s -X POST $B/disposal/selectList.do -H 'Content-Type: text/xml' \
  --data-binary '<Root xmlns="http://www.nexacroplatform.com/platform/dataset"/>'
```

Kết quả thật của bước 2 (rút gọn):
```xml
<Parameter id="ErrorCode" type="INT">0</Parameter>
<Dataset id="ds_list">
  <Row>
    <Col id="DISPOSAL_NO">S001-20260928-0001</Col> <Col id="BIZ_DT">20260928</Col>
    <Col id="STAT_CD">20</Col> <Col id="STAT_NM">확정</Col>
    <Col id="REG_ID">user</Col> <Col id="REG_DTM">20260928190558</Col>
    <Col id="LINE_CNT">1</Col> <Col id="TOT_QTY">4</Col> <Col id="TOT_COST_AMT">2800</Col>
  </Row>
</Dataset>
```

Muốn xem JSON tương ứng: dán XML vào `POST /api/nx-tools/xml-to-json` (docs/nexacro-migration/NEXACRO_XAPI_TO_JSON.md mục 5).

Gọi qua gateway `/api/nx/**` **không dùng được** cho các URL này: gateway không mang cookie session (đúng vấn đề K2 trong kế hoạch migrate). Đây là 1 điểm bro cần xử lý khi migrate.

---

## 5. Bài tập migrate

Làm theo quy trình A6 trong [docs/nexacro-migration/MIGRATION_PLAN.md](../../docs/nexacro-migration/MIGRATION_PLAN.md).

**Bước 1: Đọc theo thứ tự**
1. `lib/common.xjs`: hiểu `gfn_transaction` / `gfn_callback` (mọi lời gọi server đi qua đây), `gfn_go`, biến `gv_*`.
2. `frm_login.xfdl`: session + biến toàn cục được tạo thế nào.
3. `frm_disposal_list.xfdl` → `frm_disposal_detail.xfdl` → `frm_disposal_reg.xfdl`.

**Bước 2: Với mỗi form, điền bảng phân tích** (thành phần, Dataset, transaction, sự kiện, quy tắc, thiết bị).

**Bước 3: Lập bảng quy tắc** của cả chức năng. Với mỗi quy tắc ghi:
- Nằm ở đâu (hàm nào, form nào).
- **Server có kiểm tra lại không?** Đọc `LegacyDisposalController` + `DisposalService` để trả lời.
- Khi migrate: để ở app, ở server, hay cả hai?

**Bước 4: Trả lời các câu hỏi**
1. Khi sửa phiếu, form gửi `ds_detail:U`. Server làm gì với dòng `update` / `delete`? Android nên gửi gì?
2. `SHORT_YN` không có trong response của server. Nó từ đâu ra? Vì sao cần `useclientlayout="true"`?
3. App Android dùng JWT, server cũ dùng session cookie. Có mấy cách để Android gọi được các URL `*.do` này?
4. Số phiếu truyền giữa các form bằng `gv_disposalNo`. Android làm tương đương thế nào?
5. Có quy tắc nào **chỉ nằm ở client**? Nếu app Android bỏ sót thì chuyện gì xảy ra?

**Bước 5: Viết lại trên Android** (hoặc so với bản đáp án), rồi chạy 2 bản với cùng dữ liệu mẫu.

---

## 6. Đáp án bảng quy tắc (xem sau khi tự làm)

<details>
<summary>Mở đáp án</summary>

| # | Quy tắc | Ở đâu (Nexacro) | Server kiểm tra lại? | Bản Android (đáp án) |
|---|---|---|---|---|
| R1 | Từ ngày ≤ đến ngày | `frm_disposal_list.fn_search` | Không (chỉ lọc) | Chưa có lọc ngày trên app: **thiếu nếu thêm bộ lọc ngày** |
| R2 | Quét trùng món → +1 số lượng | `frm_disposal_reg.fn_scan` | Có (từ chối dòng trùng) | Có (`DisposalEditActivity.addScanned`) |
| R3 | Tồn khả dụng ≤ 0 lúc quét → hỏi có thêm không | `frm_disposal_reg.fn_addItem` | Không (chặn ở 확정) | Chỉ Toast cảnh báo, không hỏi: **khác hành vi** |
| R4 | Số lượng 1–9999, bắt buộc lý do, ít nhất 1 dòng | `fn_validate`, `btn_lineOk_onclick` | Có | Có (app + server) |
| R5 | **Ghi chú ≤ 100 ký tự** | `fn_validate` | **Không** | **Thiếu**: server nhận mọi độ dài, DB thật có thể lỗi khi vượt cột |
| R6 | Sửa / hủy: trạng thái 10, chưa 마감, người đăng ký hoặc 점장 | `fn_setButtons` | Có | Có (server trả `actions`) |
| R7 | Xác nhận: trạng thái 10, chưa 마감, 점장 | `fn_setButtons` | Có | Có |
| R8 | Thiếu tồn khả dụng → tô đỏ, tắt nút xác nhận | `fn_checkStock` | Có (-5) | Có |
| R9 | **Chỉ xác nhận phiếu của hôm nay** (`BIZ_DT == gv_bizDt`) | `btn_confirm_onclick` | **Không** (server cho xác nhận phiếu ngày trước chưa 마감) | **Thiếu**: app Android xác nhận được phiếu hôm qua nếu chưa 마감 |
| R10 | **Giá vốn ≥ 100.000원 → hỏi lại lần 2** | `btn_confirm_onclick` | Không (chỉ giao diện) | **Thiếu** |
| R11 | Hủy xác nhận: trạng thái 20, chưa 마감, 점장 | `fn_setButtons` | Có | Có |
| R12 | Hủy / hủy xác nhận bắt buộc lý do | `btn_rsnOk_onclick` | Có | Có |
| R13 | Hết phiên → về màn login | `gfn_callback` (-99) | – | Có cơ chế khác (JWT tự làm mới) |

**Bài học:** R5, R9, R10 là quy tắc **chỉ có ở client**. Migrate mà chỉ đọc server sẽ sót. Cần hỏi nghiệp vụ:
- R9 là quy tắc thật hay chỉ là thói quen code? Nếu thật thì phải **đưa về server**, để mọi app đều tuân theo.
- R5 phải có ở server, vì độ dài cột DB là giới hạn thật.
- R10 chỉ là giao diện: giữ ở app.

Trả lời câu hỏi bước 4:
1. Server (`save.do`) lấy các dòng hiện có, áp `insert` / `update` / `delete` theo `ITEM_CD` (dòng update dùng `OrgRow` để biết mã cũ), rồi lưu cả danh sách. Android đơn giản hơn: gửi **toàn bộ danh sách dòng** (`PUT /api/disposals/{no}`).
2. Do script `fn_checkStock` tự tính sau khi nhận dữ liệu. Không có `useclientlayout`, cột của Dataset bị thay bằng cột server gửi về, nên `SHORT_YN` biến mất.
3. (a) Android giữ cookie (`CookieJar` của OkHttp) và gọi login.do như app cũ, dùng gateway XML ↔ JSON phía client hoặc server. (b) Gateway phía server đăng nhập hộ / chuyển phiên theo user JWT. (c) Viết API JSON mới gọi lại service (cách B), dùng JWT. Bản đáp án chọn (c).
4. Truyền qua `Intent.putExtra` khi mở Activity; không dùng biến toàn cục (Android có thể kill app, biến static mất).
5. R5, R9, R10 (xem trên).

</details>

---

## 7. Đối chiếu với Nexacro 17

Nguồn: project mẫu chính thức [TOBESOFT-DOCS/sample_nexacroplatform_17](https://github.com/TOBESOFT-DOCS/sample_nexacroplatform_17) (315 form) và tài liệu TOBESOFT.

| Mục | Dùng trong bộ form này | Đối chiếu |
|---|---|---|
| Đầu file form | `<FDL version="2.1">`, `<Script type="xscript5.1">` | Project mẫu 17 có cả `2.0` và `2.1`; script đều `xscript5.1` |
| Include thư viện | `include "lib::common.xjs";` | Tài liệu: `include "ServiceID::file.xjs";` |
| Service | `form` (frm, lib), `resource` (xcss), `JSP` (server) | Tài liệu TypeDefinition 17 |
| Combo mã | `innerdataset="@ds_rsn"` | Project mẫu dùng `@ds_…` khi trỏ tới Dataset của form |
| Màu theo điều kiện trong Grid | `cssclass="expr:…"` + class trong `.xcss` | Cách project mẫu 17 và tài liệu Grid dùng |
| File xcss | `<XCSS version="1.0"><![CDATA[…]]></XCSS>` + `<Style url="xcssrc::…"/>` trong xadl | Giống project mẫu |
| Biến toàn cục | `appvariables.xml` `<AppVariables version="2.0">` | Giống project mẫu |
| Div con | `this.div_rsn.form.edt_rsn`, `addEventHandler("onclick", this.fn, this)` | Giống project mẫu |
| Chuyển form | `this.go("frm::…")` | Có trong project mẫu |
| Ô số | `Edit inputtype="number" maxlength="4"` | Có trong project mẫu |
| Tham số transaction | `nexacro.wrapQuote(…)` | Ví dụ trong tài liệu truyền tham số transaction |
| Giữ cột của client | `useclientlayout="true"` | Thuộc tính Dataset có trong tài liệu 17 |
| Font | `font="bold 14px/normal &quot;Malgun Gothic&quot;"` | Project mẫu viết `bold 12pt Arial`; nếu Studio báo lỗi, đổi sang dạng đó |

**Chưa kiểm chứng được** (cần Studio): hiển thị thực tế, `confirm()` chạy đồng bộ trên runtime mobile (nếu không, đổi sang popup có callback), thứ tự sự kiện khi `go()` sang form khác.
