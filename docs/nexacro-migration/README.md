# Migrate Nexacro 17 mobile → Android (Java)

Tài liệu chung cho việc chuyển app Nexacro 17 mobile sang Android native, **không gắn với task cụ thể**.
Phân tích từng task nằm ở thư mục riêng (ví dụ [../task-17-18-disposal/](../task-17-18-disposal/README.md)).

## Đọc theo thứ tự

| # | File | Nội dung | Khi nào đọc |
|---|---|---|---|
| 1 | [MIGRATION_PLAN.md](MIGRATION_PLAN.md) | Kế hoạch: hiện tại / đích, chỗ khó, 6 giai đoạn, checklist khảo sát, khung app, quy trình migrate 1 màn, bắt traffic, rủi ro | Đầu tiên, để thấy bức tranh chung |
| 2a | [LEARNING_PLAN_2_WEEKS.md](LEARNING_PLAN_2_WEEKS.md) | **Lộ trình 2 tuần: đọc hiểu code có sẵn + sửa nhỏ** (chức năng hủy hàng bản Nexacro ↔ Android), bảng tra nhanh, mẹo đọc code | Khi chỉ có ít thời gian trước khi vào dự án |
| 2b | [LEARNING_PLAN.md](LEARNING_PLAN.md) | Lộ trình đầy đủ 6 tuần × 5 buổi, tự làm bài tập, bài cuối tự migrate chức năng hủy hàng | Khi muốn học sâu / sau khi vào dự án |
| 3 | [NEXACRO_TO_ANDROID.md](NEXACRO_TO_ANDROID.md) | Bảng đối chiếu: Form, Dataset, Grid, Combo, popup, `transaction`, session… → Android | Tra cứu khi đọc code Nexacro / viết code Android |
| 4 | [NEXACRO_XAPI_TO_JSON.md](NEXACRO_XAPI_TO_JSON.md) | Cho Android nói chuyện với server X-API: gateway (cách A), controller JSON (cách B), công cụ XML → JSON | Tuần 2 của lộ trình; khi thiết kế lớp gọi server |
| 4b | [PLAIN_JAVA_STYLE.md](PLAIN_JAVA_STYLE.md) | Kiểu code của khách: thread + `HttpURLConnection` + `org.json`, view trong `HashMap` + 1 `OnClickListener`; so với Retrofit, các lỗi hay gặp | Ngày 4–6 của lộ trình 2 tuần; trước khi viết code trong dự án |
| 4c | [TEST_WITH_MAIN.md](TEST_WITH_MAIN.md) | Test logic bằng hàm `main` khi Gradle / JUnit / emulator không chạy được (VDI không internet): `rules/DisposalRules` + `devcheck/run.bat` | Ngày 6 của lộ trình 2 tuần; mỗi khi viết quy tắc mới |
| 5 | [MIGRATION_LAB.md](MIGRATION_LAB.md) | Bài mẫu (tra cứu sản phẩm) + 8 bài tập chuyển 1 form Nexacro | Tuần 2–5 của lộ trình |

## Vật liệu thực hành

| Ở đâu | Là gì |
|---|---|
| `nexacro-sample/frm_product_search.xfdl` | Form Nexacro 17 mẫu đơn giản (bài mẫu của LAB) |
| `nexacro-sample/disposal/` | Bộ form Nexacro 17 mobile hoàn chỉnh + `gfn_` + server X-API giả lập chạy được (bài cuối của lộ trình) |
| `nexacro-sample/XapiJsonConverter.java.txt` | Helper X-API (`com.nexacro17.xapi`) → JSON cho cách B |
| `backend/.../nexacro/` | Gateway `/api/nx/**`, công cụ `/api/nx-tools/**`, server X-API giả lập |
| `android/devcheck/` | File check có `main` + `run.bat` / `run.sh`: test `rules/DisposalRules` không cần Gradle |
| `android/.../plain/` | Chức năng hủy hàng viết theo kiểu của khách (Java thuần, không Retrofit) |
| `android/.../migration/` | Code Android của bài mẫu và màn demo X-API → JSON |
