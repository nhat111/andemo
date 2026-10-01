# Kế hoạch migrate Nexacro 17 mobile → Android (Java)

> Nhánh: `claude/android-nexacro-migration`. Cập nhật: 2026-09-30. Dự án dùng **Nexacro 17**.
> Tài liệu chung cho cả dự án, **không gắn với task cụ thể nào**. Phân tích từng task nằm ở file riêng.
> Viết khi chưa vào dự án. Những chỗ ghi "cần khảo sát" phải kiểm tra lại với code và hệ thống thật.

**Tài liệu đi kèm**

| File | Dùng khi |
|---|---|
| [LEARNING_PLAN.md](LEARNING_PLAN.md) | **Lộ trình học Android (Java) 6 tuần** theo đúng những gì việc migrate cần |
| [NEXACRO_TO_ANDROID.md](NEXACRO_TO_ANDROID.md) | Tra nhanh: khái niệm Nexacro nào tương ứng với gì trên Android |
| [NEXACRO_XAPI_TO_JSON.md](NEXACRO_XAPI_TO_JSON.md) | Cách cho Android gọi server X-API (XML Dataset) bằng JSON |
| [MIGRATION_LAB.md](MIGRATION_LAB.md) | Bài mẫu + 8 bài tập chuyển 1 form Nexacro sang Android |
| [../../nexacro-sample/disposal/README.md](../../nexacro-sample/disposal/README.md) | Bộ form Nexacro mobile hoàn chỉnh (4 form + `gfn_` + server X-API chạy được) để luyện đọc code thật và rút quy tắc |
| [ANDROID_LEARNING_PLAN.md](../ANDROID_LEARNING_PLAN.md) | 14 buổi học Android qua project PDA (chạy nền, thông báo, scanner…) |

---

## Phần A. Kế hoạch migrate

### A1. Hiện tại và đích

```
 HIỆN TẠI                                               ĐÍCH
 ───────────────────────────────────────                ───────────────────────────────────────
 PDA Android                                            PDA Android
 └─ App Nexacro mobile (runtime)                        └─ App Android native (Java)
    ├─ Form .xfdl + script, thư viện gfn_ (.xjs)           ├─ Activity / Fragment + layout XML
    ├─ Tải form mới từ server, không cài lại app           ├─ Phát hành APK (MDM / tự cập nhật)
    ├─ Đối tượng thiết bị / plugin scanner                  ├─ Scanner: intent của hãng / keyboard wedge
    └─ transaction() ──XML/SSV──┐                         └─ Retrofit ──JSON──┐
                                ▼                                              ▼
                     Server X-API (*.do)                      Gateway JSON (A) hoặc controller JSON (B)
                     Service / DAO / DB                        ──► DÙNG LẠI service / DAO / DB cũ
```

**Nguyên tắc:** chỉ thay **lớp giao diện và lớp gọi server**. Nghiệp vụ, service, DB giữ nguyên, để 2 app chạy song song trên cùng dữ liệu trong thời gian chuyển đổi.

### A2. Những chỗ khó và cách xử lý

| # | Chỗ khó | Vì sao | Cách xử lý |
|---|---|---|---|
| 1 | **Quy tắc nghiệp vụ nằm trong script** | Kiểm tra dữ liệu, tính toán, bật/tắt nút viết trong script form và `gfn_*`; thường không có tài liệu | Đọc script từng form, lập **bảng quy tắc** (A6). Quy tắc liên quan dữ liệu đưa về server để 2 app dùng chung |
| 2 | **Server trả XML Dataset** | Android dùng JSON | Bắt đầu bằng gateway (cách A, không sửa server cũ), chuyển dần sang controller JSON gọi lại service cũ (cách B) |
| 3 | **Đăng nhập / session** | Có thể dùng cookie `JSESSIONID`, hoặc gửi kèm `gv_userId`, `gv_storeCd`… trong mọi transaction | Khảo sát cách login cũ (A4). App mới (hoặc gateway) phải giữ cookie / gửi đúng tham số, hoặc chuyển sang token |
| 4 | **Tính năng thiết bị** | Scanner, camera, máy in, rung / âm báo qua đối tượng thiết bị của Nexacro hoặc plugin riêng | Viết lại bằng code native, gom vào 1 lớp dùng chung (A5) |
| 5 | **Dữ liệu offline** | Có thể lưu Dataset / SQLite cục bộ để làm việc khi mất mạng rồi gửi sau | Khảo sát có dùng không. Có thì dùng Room + hàng đợi gửi lại. **Phần tốn công nhất** nếu có |
| 6 | **Giao diện tọa độ tuyệt đối** | Form vẽ cứng theo độ phân giải 1 dòng máy | Dựng lại layout, không chép 1:1. Ưu tiên thao tác 1 tay, quét barcode, chữ đủ to |
| 7 | **Cách phát hành** | Nexacro tự tải form mới; native phải cài APK | Chốt sớm: MDM, kho ứng dụng nội bộ, hay app tự kiểm tra phiên bản và tải APK |
| 8 | **Chạy song song** | Không thay hết 1 lần; 2 app cùng ghi 1 DB | Migrate theo nhóm màn; mỗi màn so kết quả 2 app (A7); bật dần theo cửa hàng |
| 9 | **Hiệu năng máy PDA** | Máy cũ, RAM ít, Android đời thấp | Xác định `minSdk` theo máy thật; danh sách dài dùng RecyclerView + phân trang |

### A3. Các giai đoạn

Thời lượng dưới đây chỉ để hình dung thứ tự và độ lớn tương đối; con số thật phụ thuộc kết quả khảo sát (số màn, offline, cách login).

| Giai đoạn | Mục tiêu | Việc chính | Kết quả bàn giao | Xong khi |
|---|---|---|---|---|
| **0. Khảo sát** | Biết mình đang chuyển cái gì | Checklist A4; lấy mã nguồn Nexacro, danh sách màn, máy PDA thật; bắt traffic mẫu | Danh sách màn (A6) có độ khó; câu trả lời checklist; quyết định A / B | Trả lời được mọi mục "bắt buộc" trong A4 |
| **1. Khung app** | Có nền chung để làm màn nhanh | Khung project, login, lớp gọi server, xử lý lỗi, mã chung, scanner, cập nhật app (A5) | App chạy được: login → menu → gọi 1 service thật → quét barcode | Lớp chung có test; build ra APK cài được trên PDA thật |
| **2. Thí điểm** | Kiểm chứng quy trình trên 1–2 màn thật | Chọn 1 màn tra cứu + 1 màn nhập/lưu; làm đủ quy trình A6 | 2 màn chạy trên PDA thật; bảng so sánh với app cũ | Người dùng thật dùng được; không lệch dữ liệu với app cũ |
| **3. Migrate theo nhóm** | Chuyển hết các màn | Chia màn theo nghiệp vụ (nhập hàng, kiểm kê, hủy hàng, bán…); mỗi nhóm đi đủ quy trình A6 | Màn theo từng đợt; bảng theo dõi | Mọi màn trong danh sách qua "Definition of Done" (A6) |
| **4. Chạy song song** | Chuyển người dùng an toàn | Bật app mới cho vài cửa hàng thử, rồi mở rộng; theo dõi lỗi, so dữ liệu | Báo cáo thử nghiệm; hướng dẫn sử dụng | Không còn lỗi nghiêm trọng trong 1 chu kỳ nghiệp vụ (ví dụ 1 tháng có chốt sổ) |
| **5. Tắt app cũ** | Dọn dẹp | Gỡ app Nexacro khỏi PDA; chuyển gateway A sang controller B nếu chưa làm | – | Không còn thiết bị nào gọi server qua Nexacro |

### A4. Checklist khảo sát (giai đoạn 0)

**Bắt buộc**

| # | Câu hỏi | Tìm ở đâu |
|---|---|---|
| K1 | Phiên bản Nexacro và X-API? **Dự án: Nexacro 17** (đã biết); cần xác nhận bản vá (17.1.x) và phiên bản X-API (package `com.nexacro17.xapi`) | Nexacro Studio, thư viện trong `WEB-INF/lib` của server |
| K2 | Đăng nhập thế nào: session cookie, token, hay biến toàn cục gửi kèm? Hết phiên xử lý ra sao? | Form login, `Application` (`gv_*`), filter / interceptor phía server |
| K3 | Có bao nhiêu màn trên mobile? Màn nào dùng nhiều nhất? | Menu, `TypeDefinition`, thống kê truy cập nếu có |
| K4 | Scanner: dùng đối tượng thiết bị Nexacro, plugin hãng, hay keyboard wedge? Những dòng máy nào? | Script các màn có quét, cấu hình máy, danh sách thiết bị |
| K5 | Có làm việc offline không (lưu cục bộ, gửi sau)? | Script có `saveXML` / lưu file / SQLite, hỏi người dùng |
| K6 | Có in (máy in Bluetooth / mạng), chụp ảnh, ký tên, GPS không? | Script, plugin |
| K7 | Server: Java / Spring / MyBatis? Service có tách khỏi lớp X-API không (có tái dùng được không)? | Mã nguồn server |
| K8 | Phát hành app mới bằng gì (MDM nào)? | Đội hạ tầng / IT khách |
| K9 | Android thấp nhất trên PDA đang dùng? | Danh sách thiết bị |

**Nên có**

| # | Câu hỏi |
|---|---|
| K10 | Thư viện chung `gfn_*` có những hàm gì (thông báo, kiểm tra, định dạng, mã chung, quyền)? |
| K11 | Mã chung (`gds_code`…) tải lúc nào, bao lớn, có cache không? |
| K12 | Thông báo lỗi / đa ngôn ngữ lấy từ đâu (server hay file)? |
| K13 | Mạng: PDA vào server qua Wi-Fi nội bộ, VPN, hay Internet? Có proxy / chứng chỉ riêng không? |
| K14 | Có môi trường test (server + DB) riêng cho đội migrate không? |

**Môi trường làm việc (VDI của khách, không có internet)**: hỏi ngay ngày đầu, xem A10

| # | Câu hỏi |
|---|---|
| K15 | Có kho thư viện nội bộ (Nexus / Artifactory) proxy Maven Central + Google Maven không? Nếu không: thư viện được đưa vào bằng cách nào, ai duyệt? |
| K16 | Android Studio, Android SDK (platform 34, build-tools), JDK 17, Gradle (bản trong `gradle-wrapper.properties`) đã cài sẵn trong VDI chưa? |
| K17 | Máy ảo Android có chạy được trong VDI không (cần ảo hóa lồng nhau)? Nếu không: test trên thiết bị nào? |
| K18 | PDA test kết nối với VDI được không (USB redirect / ADB qua mạng)? Nếu không: cài APK lên PDA bằng gì (MDM, kênh phát hành nội bộ)? |
| K19 | Từ VDI gọi được server test không? PDA test có vào được server test không? |
| K20 | Được mang gì vào / ra VDI (tài liệu, file mẫu, log)? Được dùng công cụ gì (curl, Postman, trình duyệt)? |

### A5. Khung app cần làm trước (giai đoạn 1)

Tương ứng với những gì `Application` + `gfn_*` đang làm cho mọi form Nexacro.

| Nexacro (thường gặp) | Android | Ghi chú |
|---|---|---|
| `TypeDefinition` services (`svc::`) | `ApiClient` (Retrofit), `BuildConfig` URL theo môi trường dev / test / prod | Có sẵn mẫu: `api/ApiClient.java`, `apiBaseUrl` trong `gradle.properties` |
| `transaction` + `fn_callback` chung | Lớp gọi server chung: loading, mã lỗi, hết phiên, mất mạng | Mẫu: `NexacroGatewayApi` + `NxResponse`, `ApiErrorDto` |
| Login, `gv_userId`, `gv_storeCd` | Màn login + lưu phiên (`SharedPreferences`), interceptor gắn header / cookie | Token: `PreferenceManager`, `TokenAuthenticator`. Nếu phải gọi thẳng `*.do` cũ dùng session (như `nexacro-sample/disposal`): OkHttp `CookieJar` giữ `JSESSIONID`, xử lý `ErrorCode -99` → login lại |
| `gds_code` (mã chung) | Tải 1 lần sau login, cache trong bộ nhớ (+ Room nếu cần offline) | |
| `gfn_alert` / `gfn_confirm` / thông báo | Lớp tiện ích dialog / Toast, bảng thông báo theo mã | |
| Kiểm tra quyền, menu theo user | Màn menu dựng theo quyền server trả về | Server quyết định quyền; app chỉ hiển thị |
| Scanner (đối tượng thiết bị / plugin) | 1 lớp scanner dùng chung: intent của hãng + keyboard wedge + camera (ZXing) | Mẫu: `QrScanActivity` |
| Tự tải form mới | Kiểm tra phiên bản khi mở app, bắt buộc cập nhật nếu quá cũ | Tránh app cũ ghi dữ liệu sai quy tắc mới |
| Log / trace | Log có mức (debug / lỗi), gửi lỗi về server nếu cần | |

### A6. Quy trình migrate 1 màn

**Bảng phân tích màn** (điền cho mỗi form trước khi viết code):

| Mục | Nội dung |
|---|---|
| Form | `frm_xxx.xfdl`, form con / popup, `.xjs` dùng |
| Mục đích | Người dùng làm gì ở màn này |
| Thành phần | Edit, Combo, Grid (cột nào), Button… |
| Dataset | Tên, cột, kiểu, dataset nào gửi lên / nhận về |
| Transaction | URL `*.do`, in / out dataset, tham số, callback |
| Sự kiện | `onload`, `onclick`, `oncellclick`, `onchanged`… làm gì |
| **Quy tắc** | Mỗi `if` trong script là 1 dòng: điều kiện → hành động / thông báo |
| Thiết bị | Có quét / in / chụp ảnh không |
| Offline | Có lưu cục bộ không |

**Ví dụ rút quy tắc từ script:**

```javascript
this.btn_save_onclick = function () {
    if (this.gfn_isNull(this.ds_master.getColumn(0, "STORE_CD"))) {   // Q1
        this.gfn_alert("MSG_REQUIRED", ["점포"]); return;
    }
    if (this.ds_master.getColumn(0, "STATUS") != "10") {               // Q2
        this.gfn_alert("MSG_INVALID_STATUS"); return;
    }
    for (var i = 0; i < this.ds_detail.rowcount; i++) {
        if (this.ds_detail.getColumn(i, "QTY") <= 0) {                  // Q3
            this.gfn_alert("MSG_QTY_POSITIVE", [i + 1]); return;
        }
    }
    if (!this.gfn_confirm("MSG_CONFIRM_SAVE")) return;                  // Q4
    this.transaction("save", "svc::xxx/save.do", "ds_master=ds_master ds_detail=ds_detail:U", "", "", "fn_callback");
};
```

| # | Quy tắc | Đưa về | Ghi chú |
|---|---|---|---|
| Q1 | Bắt buộc có mã cửa hàng | Server + app | App kiểm tra để báo sớm, server kiểm tra lại |
| Q2 | Chỉ lưu khi trạng thái `10` | **Server** | Quy tắc trạng thái phải ở server, nếu không 2 app có thể làm khác nhau |
| Q3 | Số lượng từng dòng > 0 | Server + app | Báo đủ mọi dòng sai 1 lần |
| Q4 | Hỏi lại trước khi lưu | App | Chỉ là giao diện |
| – | `ds_detail:U` chỉ gửi dòng thay đổi | App | Android gửi cả danh sách, hoặc tự đánh dấu dòng đổi (NEXACRO_TO_ANDROID.md mục 4.3) |

**Các bước:**

1. Điền bảng phân tích + bảng quy tắc.
2. Bắt traffic thật của màn (A7), lưu vài mẫu request / response làm dữ liệu so sánh.
3. Chọn cách gọi server: gateway (A) hay controller JSON (B). Thử bằng curl trước khi viết màn.
4. Thiết kế lại giao diện cho PDA (không chép tọa độ).
5. Viết model → API interface → layout → Activity / Adapter.
6. Đưa quy tắc dữ liệu về server nếu làm được; app chỉ kiểm tra để báo sớm.
7. So kết quả 2 app với cùng dữ liệu (A7).
8. Test trên PDA thật: quét, xoay / tắt màn hình, mất mạng giữa chừng, mạng chậm.

**Definition of Done cho 1 màn**

- [ ] Bảng phân tích và bảng quy tắc đã được người hiểu nghiệp vụ xem lại
- [ ] Mọi quy tắc trong bảng có ở app mới (và server nếu là quy tắc dữ liệu)
- [ ] Kết quả trùng với app cũ trên bộ dữ liệu mẫu
- [ ] Chạy trên PDA thật: quét barcode, mất mạng, mạng chậm, về background rồi quay lại
- [ ] Không crash khi server trả lỗi / hết phiên
- [ ] Có mặt trong bảng theo dõi migrate

### A7. Bắt traffic và so sánh 2 app

App Nexacro mobile không có DevTools như trình duyệt. Cách lấy request / response thật:

| Cách | Làm thế nào | Lưu ý |
|---|---|---|
| **Log phía server** | Ghi request / response của các URL `*.do` (filter Servlet hoặc log của X-API) ở môi trường test | Dễ nhất, không đụng thiết bị. Không bật ở production nếu có dữ liệu nhạy cảm |
| **Proxy** (Charles, mitmproxy) | PDA dùng Wi-Fi có proxy trỏ về máy tính | HTTPS: phải cài chứng chỉ của proxy lên máy và app phải tin chứng chỉ người dùng; có thể không làm được trên bản phát hành |
| **Nexacro Studio chạy bản HTML5** (nếu dự án có) | Chạy form trên trình duyệt, xem tab Network | Cùng server, cùng Dataset |

Có XML rồi:
- Dán vào công cụ `POST /api/nx-tools/xml-to-json` (xem NEXACRO_XAPI_TO_JSON.md mục 5) để xem JSON app mới nhận.
- Lưu cặp (request, response) làm **dữ liệu kiểm thử**: chạy lại qua app / API mới, so từng Dataset.

### A8. Rủi ro

| Rủi ro | Dấu hiệu | Giảm thiểu |
|---|---|---|
| Sót quy tắc ẩn trong script | App mới cho lưu dữ liệu app cũ chặn | Bảng quy tắc bắt buộc cho mỗi màn; người nghiệp vụ duyệt; đưa quy tắc về server |
| 2 app ghi dữ liệu khác nhau trong thời gian song song | Số liệu lệch, báo cáo sai | Dùng chung service server; so dữ liệu mẫu; bật dần theo cửa hàng |
| Offline phức tạp hơn dự kiến | Khảo sát thấy lưu cục bộ nhiều | Tách thành hạng mục riêng, làm sau khi có khung app |
| Scanner khác nhau giữa các dòng máy | Máy A quét được, máy B không | Lớp scanner chung hỗ trợ cả intent và keyboard wedge; test đủ dòng máy |
| Quy trình phát hành APK chưa có | Không cập nhật được máy ở cửa hàng | Chốt MDM / tự cập nhật từ giai đoạn 1 |
| Session / đăng nhập khác cách cũ | Bị đăng xuất liên tục, gọi sai cửa hàng | Khảo sát K2 trước khi làm khung app |
| Máy PDA cũ, chậm | Danh sách giật, hết bộ nhớ | Phân trang, không tải hết; test trên máy chậm nhất |
| VDI không có internet | Gradle không tải được thư viện; không có máy ảo; không cài được APK để thử | Hỏi K15–K20 ngày đầu; chốt danh sách thư viện sớm; tách logic để test JUnit (A10) |

### A9. Bảng theo dõi (mẫu)

| # | Nhóm | Màn Nexacro | Màn Android | Cách gọi server (A/B) | Quy tắc | Độ khó | Trạng thái |
|---|---|---|---|---|---|---|---|
| 1 | Chung | Login | `LoginActivity` | – | – | – | ⬜ |
| 2 | … | `frm_xxx.xfdl` | | | 0 / N | Thấp / TB / Cao | ⬜ Phân tích · ⬜ Code · ⬜ So sánh · ⬜ Test PDA |

---

### A10. Làm việc trong VDI không có internet

**Vấn đề và cách xử lý**

| Vấn đề | Cách xử lý (tùy chính sách khách) |
|---|---|
| Gradle cần tải thư viện (`dependencies` trong `app/build.gradle`) và chính Gradle (`gradle-wrapper.properties`) | Tốt nhất: kho nội bộ (K15), đổi `repositories` trong `settings.gradle` sang URL nội bộ. Nếu không có: xin IT đưa vào bộ cache Gradle / thư mục Maven cục bộ đã tải sẵn từ máy có mạng, rồi build với `--offline` |
| Danh sách thư viện | Chốt sớm và **ít**: AppCompat, Material (kèm RecyclerView), ConstraintLayout, Retrofit + Gson, OkHttp logging, ZXing (nếu quét bằng camera), JUnit (test). Mỗi thư viện thêm sau = 1 lần xin duyệt |
| Không có máy ảo | Build để bắt lỗi (Ctrl+F9) · xem trước layout (tab Design) · JUnit cho logic · thử API bằng curl / Postman tới server test · cài APK lên PDA thật theo kênh của khách (K18) |
| Không tra được tài liệu | Mang vào (nếu được phép, K20) bảng tra `NEXACRO_TO_ANDROID.md`, `LEARNING_PLAN_2_WEEKS.md` mục 4; Android Studio vẫn xem được Javadoc của thư viện đã tải |
| Không gửi code / log ra ngoài | Tuân thủ chính sách khách: **không chép code, dữ liệu, log của khách ra máy ngoài hoặc dán vào công cụ bên ngoài** khi chưa được phép. Khi cần hỏi: tự viết lại tình huống bằng ví dụ giả |

**Vòng làm việc hằng ngày khi không chạy được app trong VDI**

1. Thử API (curl / Postman) tới server test → biết chắc request / response.
2. Viết model + API interface + màn hình → **build** (Ctrl+F9) sau mỗi thay đổi.
3. Logic (kiểm tra dữ liệu, tính tiền, đổi định dạng, đọc lỗi) đặt trong class Java thường → **JUnit**.
4. Xem layout ở tab Design.
5. Cuối ngày / cuối đợt: build APK → cài lên PDA test theo kênh của khách → bấm thử, ghi lỗi.

## Phần B. Lộ trình học

Đã tách thành file riêng: **[LEARNING_PLAN.md](LEARNING_PLAN.md)** (6 tuần, từng buổi, bài tập dùng code trong repo, bài cuối là migrate chức năng hủy hàng từ bộ form Nexacro 17 mẫu).
