# Migrate Nexacro 17 mobile → Android (Java)

Tài liệu chung cho việc chuyển app Nexacro 17 mobile sang Android native, **không gắn với task cụ thể**.
Phân tích từng task nằm ở thư mục riêng (ví dụ [../task-17-18-disposal/](../task-17-18-disposal/README.md)).

## Đọc theo thứ tự

| # | File | Nội dung | Khi nào đọc |
|---|---|---|---|
| 1 | [MIGRATION_PLAN.md](MIGRATION_PLAN.md) | Kế hoạch: hiện tại / đích, chỗ khó, 6 giai đoạn, checklist khảo sát, khung app, quy trình migrate 1 màn, bắt traffic, rủi ro | Đầu tiên, để thấy bức tranh chung |
| 2 | [LEARNING_PLAN.md](LEARNING_PLAN.md) | **Lộ trình học Android (Java) 6 tuần × 5 buổi**, bài tập dùng code trong repo, bài cuối migrate chức năng hủy hàng | Học hằng ngày |
| 3 | [NEXACRO_TO_ANDROID.md](NEXACRO_TO_ANDROID.md) | Bảng đối chiếu: Form, Dataset, Grid, Combo, popup, `transaction`, session… → Android | Tra cứu khi đọc code Nexacro / viết code Android |
| 4 | [NEXACRO_XAPI_TO_JSON.md](NEXACRO_XAPI_TO_JSON.md) | Cho Android nói chuyện với server X-API: gateway (cách A), controller JSON (cách B), công cụ XML → JSON | Tuần 2 của lộ trình; khi thiết kế lớp gọi server |
| 5 | [MIGRATION_LAB.md](MIGRATION_LAB.md) | Bài mẫu (tra cứu sản phẩm) + 8 bài tập chuyển 1 form Nexacro | Tuần 2–5 của lộ trình |

## Vật liệu thực hành

| Ở đâu | Là gì |
|---|---|
| `nexacro-sample/frm_product_search.xfdl` | Form Nexacro 17 mẫu đơn giản (bài mẫu của LAB) |
| `nexacro-sample/disposal/` | Bộ form Nexacro 17 mobile hoàn chỉnh + `gfn_` + server X-API giả lập chạy được (bài cuối của lộ trình) |
| `nexacro-sample/XapiJsonConverter.java.txt` | Helper X-API (`com.nexacro17.xapi`) → JSON cho cách B |
| `backend/.../nexacro/` | Gateway `/api/nx/**`, công cụ `/api/nx-tools/**`, server X-API giả lập |
| `android/.../migration/` | Code Android của bài mẫu và màn demo X-API → JSON |
