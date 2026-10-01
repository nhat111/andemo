# Lộ trình học Android (Java) để migrate Nexacro 17 mobile

> 📚 [Mục lục tài liệu](README.md) · Lộ trình: [2 tuần](LEARNING_PLAN_2_WEEKS.md) · Làm 1 màn: [công thức](HOW_TO_CODE_A_SCREEN.md)

> Dành cho: dev Java đã lâu chưa code Android, sắp tham gia dự án chuyển app **Nexacro 17 mobile** sang **Android native (Java)**.
> Nhánh: `claude/android-nexacro-migration`. Cập nhật: 2026-09-30.
> Lộ trình được rút ra từ việc rà soát [MIGRATION_PLAN.md](MIGRATION_PLAN.md): mỗi kỹ năng ở đây đều cần cho một bước migrate cụ thể.
> Chỉ có 2 tuần? Dùng bản rút gọn [LEARNING_PLAN_2_WEEKS.md](LEARNING_PLAN_2_WEEKS.md) (đọc hiểu code có sẵn + sửa nhỏ).

---

## 1. Cách học

- **6 tuần × 5 buổi, mỗi buổi khoảng 2 giờ.** Học buổi tối hay cuối tuần đều được; quan trọng là đi đúng thứ tự.
- Mỗi buổi có 3 phần: **Học** (đọc / xem code), **Làm** (sửa code, chạy thử), **Xong khi** (tự kiểm tra được).
- Học **cả 2 phía**: đọc Nexacro 17 (để hiểu code cũ) và viết Android (để làm code mới). Bài cuối là migrate trọn 1 chức năng.
- Luôn tự hỏi: "Trong Nexacro cái này là gì?" → tra [NEXACRO_TO_ANDROID.md](NEXACRO_TO_ANDROID.md).
- Code bài tập đặt trong package `com.example.andemo.migration` (hoặc nhánh riêng của bro), commit sau mỗi bài.
- Build Variant dùng khi học: **`pollingDebug`** (không cần Firebase).

**Vật liệu trong repo**

| Vật liệu | Dùng ở tuần |
|---|---|
| `nexacro-sample/disposal/` (4 form Nexacro 17 + `gfn_` + server X-API giả lập) và README của nó | 1, 2, 6 |
| `nexacro-sample/frm_product_search.xfdl` + [MIGRATION_LAB.md](MIGRATION_LAB.md) (bài mẫu + 8 bài tập) | 2, 3, 4, 5 |
| [NEXACRO_XAPI_TO_JSON.md](NEXACRO_XAPI_TO_JSON.md) + màn "Demo X-API → JSON" | 2 |
| Code Android chức năng hủy hàng `android/.../disposal/` (**đáp án** của bài cuối) | 4, 6 |
| [../ANDROID_LEARNING_PLAN.md](../ANDROID_LEARNING_PLAN.md) (14 buổi, project PDA) | tham khảo thêm ở tuần 1, 2, 5 |
| [../LOCAL_SETUP_GUIDE.md](../LOCAL_SETUP_GUIDE.md) | buổi 0 |

---

## 2. Buổi 0: Chuẩn bị (1 lần)

- [ ] Android Studio + máy ảo Android (hoặc máy thật qua Wireless debugging), theo [../LOCAL_SETUP_GUIDE.md](../LOCAL_SETUP_GUIDE.md)
- [ ] Checkout nhánh `claude/android-nexacro-migration`
- [ ] Chạy backend (`backend/`, IntelliJ hoặc `mvn spring-boot:run`); mở `http://localhost:8080/api/health` thấy `UP`
- [ ] `android/gradle.properties`: `apiBaseUrl=http://10.0.2.2:8080/` (máy ảo) → Sync
- [ ] Build Variant `pollingDebug` → Run → login `user` / `123456`
- [ ] VS Code (hoặc editor bất kỳ) để đọc file `.xfdl`, `.xjs`
- [ ] Có `curl` (Git Bash trên Windows có sẵn)

**Không cần cài Nexacro Studio** để theo lộ trình này.

---

## 3. Tổng quan 6 tuần

| Tuần | Chủ đề | Kết quả cuối tuần |
|---|---|---|
| **1** | Đọc Nexacro 17 + nền tảng Android | Giải thích được 1 form Nexacro; tự tạo 1 màn Android và chuyển dữ liệu giữa 2 màn |
| **2** | Gọi server: Retrofit, lỗi, token, X-API ↔ JSON, session cookie | Gọi được API JSON và service `*.do` cũ; xử lý lỗi không crash |
| **3** | Danh sách và nhập liệu: RecyclerView, Spinner, dialog, validation | Màn có Grid + điều kiện lọc + form nhập kiểm tra dữ liệu |
| **4** | Nhiều màn, trạng thái, quy tắc nghiệp vụ | Popup trả kết quả, ViewModel; hiểu vì sao đưa quy tắc về server |
| **5** | Thiết bị PDA, dữ liệu lớn, phát hành | Quét barcode (camera + scanner), phân trang, cài APK lên máy thật |
| **6** | **Bài cuối:** migrate chức năng hủy hàng từ bộ form Nexacro 17 | Bảng phân tích + bảng quy tắc + bổ sung quy tắc còn thiếu + so sánh 2 bản |

---

## 4. Chi tiết từng tuần

### Tuần 1: Đọc Nexacro 17 + nền tảng Android

**Vì sao:** migrate bắt đầu bằng **đọc code cũ** (quy trình A6, bước 1). Không đọc được form Nexacro thì không biết phải làm gì.

| Buổi | Học | Làm | Xong khi |
|---|---|---|---|
| 1 | Cấu trúc project Nexacro 17: TypeDefinition (services `frm`, `lib`, `svc`), `appvariables.xml` (`gv_*`), file `.xfdl` gồm `Layouts` / `Objects` (Dataset) / `Bind` / `Script` | Đọc `nexacro-sample/disposal/README.md` mục 1–4 (cấu trúc, luồng đầy đủ, cấu hình project, danh sách transaction), `disposal.xadl`, `typedefinition.xml`, `appvariables.xml`, `lib/common.xjs` | Giải thích được `gfn_transaction` → `gfn_callback` → callback của form; `-99` dẫn về đâu |
| 2 | Dataset: `ColumnInfo`, `getColumn` / `setColumn`, `rowcount`, rowtype (normal / insert / update / delete), `ds:U`; `transaction(svcId, url, in, out, args, callback)` | Đọc `frm_login.xfdl`, `frm_disposal_list.xfdl`. Chạy backend, làm theo README mục 5 (login + tra cứu bằng curl, có / không có cookie) | Tự viết được lệnh curl gọi `selectDetail.do` cho 1 phiếu và đọc hiểu XML trả về |
| 3 | Project Android: Gradle, build variant (`polling` / `websocket` / `fcm`), `AndroidManifest.xml`, `res/layout`, `res/values`, `BuildConfig` | Mở `android/app/build.gradle`, `AndroidManifest.xml`; Run app, login, bấm các nút ở màn chính | Chỉ ra được: màn nào khai báo ở đâu, URL server lấy từ đâu |
| 4 | Activity và vòng đời: `onCreate` → `onStart` → `onResume` → `onPause` → `onStop` → `onDestroy`; xoay màn hình = tạo lại Activity | Đặt breakpoint / `Log.d` ở các hàm vòng đời của `migration/ProductSearchActivity`; xoay màn hình, bấm Home, quay lại | Giải thích được vì sao xoay màn hình thì `onCreate` chạy lại và API bị gọi lại |
| 5 | Intent, `putExtra` / `getStringExtra` (thay cho `gv_*` + `go()`); layout XML (`LinearLayout`, `ConstraintLayout`) | Tạo `migration/HelloActivity`: 2 ô nhập + nút → mở `HelloResultActivity` hiển thị dữ liệu vừa nhập; khai báo trong Manifest; thêm nút ở `activity_main.xml` | Chạy được; giải thích vì sao Android không truyền dữ liệu qua biến toàn cục như `gv_disposalNo` |

**Tự kiểm tra:** `useclientlayout="true"` để làm gì? `ds_detail:U` gửi những dòng nào? `this.go("frm::…")` tương ứng gì trên Android?

---

### Tuần 2: Gọi server

**Vì sao:** thay `transaction()` bằng Retrofit là việc lặp lại ở **mọi màn** (A5 – khung app). Server cũ nói XML + session, server mới nói JSON + token: phải nắm cả hai.

| Buổi | Học | Làm | Xong khi |
|---|---|---|---|
| 6 | Retrofit + OkHttp + Gson: interface API (`@GET`, `@POST`, `@Query`, `@Path`, `@Body`), model POJO ↔ JSON | Đọc `api/ApiClient.java`, `api/ProductService.java`, `model/ProductDto.java`; xem Logcat OkHttp khi mở màn tra cứu sản phẩm | Vẽ được luồng: nút Tìm → `enqueue` → server → `onResponse` → Adapter |
| 7 | `enqueue` vs `execute`, luồng giao diện; `isFinishing()` trong callback; `onFailure` (mất mạng) vs `!isSuccessful()` (lỗi HTTP); đọc `errorBody` | [MIGRATION_LAB.md](MIGRATION_LAB.md) **bài 1**; tắt backend rồi bấm Tìm; đọc `model/ApiErrorDto.java` | Mất mạng / server lỗi đều hiện thông báo đúng, không crash |
| 8 | Xác thực bằng token: `AuthInterceptor` gắn header, `TokenAuthenticator` + `TokenRefresher` tự làm mới khi 401 | Đọc 3 file trên + `util/PreferenceManager.java`; giảm `jwt.expiration` backend xuống 60000 (1 phút), dùng app qua 1 phút, xem Logcat | Giải thích được khác biệt JWT (app mới) và session cookie (app Nexacro cũ) |
| 9 | Nói chuyện với server X-API: XML Dataset ↔ JSON, cách A (gateway) và cách B (controller JSON) | Đọc [NEXACRO_XAPI_TO_JSON.md](NEXACRO_XAPI_TO_JSON.md), chạy 4 ví dụ curl; mở màn "Demo X-API → JSON"; đọc `NxRequest`, `NxResponse.dataset(...)` | Tự thêm 1 nút demo gọi `code/list` và hiện `ds_unit` bằng `List<Model>` |
| 10 | Session cookie: vì sao gateway không gọi được `disposal/*.do`; OkHttp `CookieJar` | **Bài mở rộng:** viết chương trình Java nhỏ (chạy trên JVM, không cần Android) dùng OkHttp + `CookieJar` giữ cookie trong bộ nhớ: gọi `login.do` rồi `selectList.do`; in ra các `DISPOSAL_NO` | Có cookie → nhận `ds_list`; bỏ cookie → nhận `ErrorCode -99` |

**Tự kiểm tra:** 1 lời gọi Retrofit có mấy nhánh kết quả? Tại sao không gọi `execute()` trên luồng giao diện? Có mấy cách để app Android dùng được service `*.do` cần session?

---

### Tuần 3: Danh sách và nhập liệu

**Vì sao:** phần lớn màn nghiệp vụ = **Grid + điều kiện tìm + form nhập**. Đây là khối lượng chính khi migrate.

| Buổi | Học | Làm | Xong khi |
|---|---|---|---|
| 11 | RecyclerView: Adapter, ViewHolder, LayoutManager; vì sao ViewHolder được tái sử dụng | Đọc `migration/ProductAdapter.java`, so từng phần với Grid trong `frm_product_search.xfdl` (bảng mục 3 của LAB) | Giải thích được "luôn gán cả 2 nhánh màu" |
| 12 | Thêm điều kiện tìm: CheckBox, tham số `@Query` tùy chọn | LAB **bài 2** (cả backend + app, có test backend) | Tick "Chỉ còn hàng" lọc đúng; test backend pass |
| 13 | Spinner + `ArrayAdapter`, `toString()` của model; mã chung (Combo `innerdataset` → Spinner) | LAB **bài 3** | Chọn loại hàng lọc đúng; xoay màn hình không crash |
| 14 | Dialog (`AlertDialog`, `setView`), `EditText.setError`, kiểm tra dữ liệu (thay `gfn_alert`, `gfn_isNull`) | LAB **bài 5** phần app: màn nhập kho với số lượng, ghi chú, kiểm tra dữ liệu | Nhập sai bị chặn và báo đúng ô |
| 15 | POST có body, đọc lỗi 400 của server, chống bấm 2 lần | LAB **bài 5** phần server + nối app; ôn tuần | Lưu thành công thì tồn kho tăng; lỗi server hiện đúng thông báo |

**Tự kiểm tra:** Grid Nexacro tự vẽ lại khi Dataset đổi; Android thì sao? Quy tắc kiểm tra dữ liệu nên đặt ở app, server hay cả hai?

---

### Tuần 4: Nhiều màn, trạng thái, quy tắc nghiệp vụ

**Vì sao:** migrate sai thường không phải do giao diện mà do **sót quy tắc** và **mất trạng thái** (A2 #1, A8).

| Buổi | Học | Làm | Xong khi |
|---|---|---|---|
| 16 | Popup trả kết quả: `ActivityResultLauncher` (thay `showModal` + `close(ret)`, hoặc Div ẩn / hiện trên mobile) | LAB **bài 4** | Chọn sản phẩm trả đúng về màn nhập kho; Back không đổi gì |
| 17 | ViewModel + LiveData: giữ dữ liệu khi xoay màn hình | LAB **bài 8** | Xoay màn hình không gọi lại API (xem Logcat) |
| 18 | Server quyết định quyền, app chỉ hiển thị (`actions`, `issues`) | So `DisposalDetailActivity.render` (Android) với `fn_setButtons` + `fn_checkStock` trong `frm_disposal_detail.xfdl` | Liệt kê được quy tắc nào trong `fn_setButtons` đã chuyển về server |
| 19 | Transaction phía server: `@Transactional`, khóa dòng, `@Version`, đảo chứng từ | Đọc `backend/.../disposal/DisposalService.java` (`confirm`, `cancelConfirm`); chạy `DisposalIntegrationTest` | Giải thích được vì sao 2 người bấm xác nhận cùng lúc chỉ trừ tồn 1 lần |
| 20 | Xử lý lỗi theo nghĩa: 400 / 403 / 409 / 422; mất mạng giữa lúc ghi | Trên app: xác nhận phiếu thiếu tồn (422), sửa phiếu bằng 2 tài khoản cùng lúc (409), tắt mạng khi đang bấm Xác nhận | Mô tả được app xử lý từng trường hợp thế nào và vì sao "tải lại, không gửi lại mù" |

**Tự kiểm tra:** Quy tắc chỉ nằm ở script client có nguy cơ gì khi có 2 app chạy song song? Vì sao không truyền dữ liệu giữa màn bằng biến static?

---

### Tuần 5: Thiết bị PDA, dữ liệu lớn, phát hành

**Vì sao:** app chạy trên **PDA ở cửa hàng**: phải quét được, chạy được trên máy cũ, cài được lên máy thật (A2 #4, #7, #9).

| Buổi | Học | Làm | Xong khi |
|---|---|---|---|
| 21 | Quyền lúc chạy (camera); quét bằng camera (ZXing) | Đọc `QrScanActivity.java` phần camera; [../ANDROID_LEARNING_PLAN.md](../ANDROID_LEARNING_PLAN.md) buổi 5 | Quét QR bằng camera máy ảo / điện thoại ra đúng sản phẩm |
| 22 | Scanner cứng: intent của hãng (DataWedge…) và keyboard wedge (gõ phím + Enter) | Đọc `QrScanActivity` phần intent + `disposal/DisposalEditActivity.addScanned`; LAB **bài 6**; ANDROID_LEARNING_PLAN buổi 13 | Quét / gõ barcode + Enter tự thêm hàng; quét trùng thì +1 |
| 23 | Danh sách lớn: phân trang, `notifyItemRangeInserted` | LAB **bài 7** | Cuộn mượt 500 dòng |
| 24 | Lưu cục bộ: `SharedPreferences`, giới thiệu Room; ý tưởng hàng đợi gửi lại khi offline | ANDROID_LEARNING_PLAN buổi 10; đọc `PreferenceManager` | Giải thích được khi nào cần Room (câu hỏi K5 của khảo sát) |
| 25 | Build và phát hành: build variant, `versionCode`, APK debug / release, cài qua Wireless debugging; MDM là gì | Build APK `pollingDebug`, cài lên máy thật; đọc `build.gradle` phần flavors | Cài và chạy được trên máy thật; biết APK nằm ở đâu |

**Tự kiểm tra:** Scanner keyboard wedge khác scanner intent thế nào? Nexacro tự tải form mới, app native cập nhật bằng cách nào?

---

### Tuần 6: Bài cuối, migrate chức năng hủy hàng

**Vì sao:** làm đúng quy trình A6 trên 1 chức năng trọn vẹn: đọc code cũ → rút quy tắc → chuyển sang Android → so sánh.

Vật liệu: `nexacro-sample/disposal/` (README mục 2 luồng đầy đủ, mục 6 bài tập, mục 7 đáp án). Đáp án Android: `android/.../disposal/` + [../task-17-18-disposal/README.md](../task-17-18-disposal/README.md). **Không mở đáp án trước buổi 29.**

| Buổi | Làm | Xong khi |
|---|---|---|
| 26 | Bước 1–2: đọc 4 form theo thứ tự, điền **bảng phân tích màn** (mẫu A6) cho từng form | 4 bảng phân tích |
| 27 | Bước 3: lập **bảng quy tắc** của cả chức năng; mỗi quy tắc ghi server có kiểm tra lại không (đọc `LegacyDisposalController`, `DisposalService`) | Tìm được các quy tắc **chỉ có ở client** trong bản Nexacro (đáp án: README mục 7 có 5 quy tắc) |
| 28 | Tự đặt thêm 1 quy tắc mới (ví dụ "SL hủy mỗi dòng ≤ 50% tồn thực tế" hoặc "phiếu có lý do 리콜 phải có ghi chú"): viết vào **server** (`DisposalService` + test) và **app** (báo sớm). Xem cách R5, R9 đã được đưa về server (`validRemark`, `requireToday`) để làm theo | Test backend mới pass; app báo đúng |
| 29 | Bước 4: trả lời 5 câu hỏi trong README; chạy cùng dữ liệu qua `*.do` (curl) và API JSON / app; mở **đáp án**, so với bảng của mình | Ghi lại chỗ khác nhau giữa bảng của mình và đáp án, và vì sao |
| 30 | Đi qua checklist **Definition of Done** (A6); viết 1 trang tóm tắt: đã chuyển gì, quy tắc nào đưa về server, còn rủi ro gì | Có bản tóm tắt để trình bày với team |

---

## 5. Sau 6 tuần (khi dự án cần)

| Chủ đề | Khi nào cần | Tài liệu |
|---|---|---|
| Room + làm việc offline, WorkManager gửi lại | Khảo sát thấy app cũ có offline (K5) | developer.android.com: Room, WorkManager |
| Test tự động (JUnit cho logic, Espresso cho giao diện) | Khi bắt đầu giai đoạn 3 (migrate theo nhóm) | developer.android.com: Testing |
| Chạy nền, thông báo, WebSocket, FCM | Nếu có chức năng nhận lệnh / thông báo | [../ANDROID_LEARNING_PLAN.md](../ANDROID_LEARNING_PLAN.md) buổi 6–12 |
| In qua máy in Bluetooth / mạng | Khảo sát K6 | Tài liệu SDK của hãng máy in |
| Kotlin / Jetpack Compose | Nếu dự án chọn | developer.android.com |

---

## 6. Khi bị kẹt

| Hiện tượng | Xem ở đâu |
|---|---|
| App crash | Logcat, lọc `package:mine level:error`; đọc dòng `Caused by` |
| Gọi API không ra dữ liệu | Logcat OkHttp (bản debug in cả request / response); thử cùng URL bằng curl |
| Không hiểu 1 hàm Nexacro | Tìm trong [NEXACRO_TO_ANDROID.md](NEXACRO_TO_ANDROID.md); tra docs.tobesoft.com (Nexacro 17) |
| Không hiểu XML server trả | Dán vào `POST /api/nx-tools/xml-to-json` |
| Xoay màn hình mất dữ liệu | Tuần 4 buổi 17 (ViewModel) |

---

## 7. Theo dõi tiến độ

| Tuần | Buổi 1 | Buổi 2 | Buổi 3 | Buổi 4 | Buổi 5 | Ghi chú |
|---|---|---|---|---|---|---|
| 0 | ⬜ chuẩn bị | | | | | |
| 1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | |
| 2 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | |
| 3 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | |
| 4 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | |
| 5 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | |
| 6 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | |

**Tài liệu tham khảo chung:** [developer.android.com/guide](https://developer.android.com/guide) · [Vòng đời Activity](https://developer.android.com/guide/components/activities/activity-lifecycle) · [RecyclerView](https://developer.android.com/develop/ui/views/layout/recyclerview) · [ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel) · [Room](https://developer.android.com/training/data-storage/room) · [Retrofit](https://square.github.io/retrofit/) · [Nexacro 17 docs](https://docs.tobesoft.com/developer_guide_nexacro_17_ko/18b5d4760a521cce)
