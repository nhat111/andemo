# Mục lục tài liệu: migrate Nexacro 17 mobile → Android (Java)

Mọi file đều có dòng **📚 Mục lục tài liệu** ở đầu để quay lại đây. Chỉ cần nhớ file này.

## Bắt đầu từ đây

| Đang ở giai đoạn | Đọc | Rồi |
|---|---|---|
| **Học** (2 tuần trước khi vào dự án) | [LEARNING_PLAN_2_WEEKS.md](LEARNING_PLAN_2_WEEKS.md): làm theo từng ngày, trong mỗi ngày đã có link tới đúng file cần đọc | Hết 2 tuần → giai đoạn Làm |
| **Làm** (tự migrate 1 màn) | [HOW_TO_CODE_A_SCREEN.md](HOW_TO_CODE_A_SCREEN.md): 8 bước, khung code copy được, cuối cùng mới nhờ AI review | Kẹt bước nào → bảng "Kẹt thì tra ở đâu" cuối file đó |
| **Lập kế hoạch** (lead / đầu dự án) | [MIGRATION_PLAN.md](MIGRATION_PLAN.md) | |

## Tìm theo câu hỏi

| Câu hỏi | File |
|---|---|
| Hôm nay học gì? | [LEARNING_PLAN_2_WEEKS.md](LEARNING_PLAN_2_WEEKS.md) |
| Code 1 màn mới theo các bước nào? Khung code ở đâu? | [HOW_TO_CODE_A_SCREEN.md](HOW_TO_CODE_A_SCREEN.md) |
| Code của khách (thread + `HttpURLConnection`, view trong HashMap, custom adapter) viết thế nào, lỗi hay gặp? | [PLAIN_JAVA_STYLE.md](PLAIN_JAVA_STYLE.md) |
| Code đỏ / không build / không có emulator trong VDI thì kiểm tra bằng gì? Checklist không sót bước? | [WORK_WITHOUT_BUILD.md](WORK_WITHOUT_BUILD.md) |
| Dataset / Grid / Combo / `transaction` / popup của Nexacro tương ứng gì trên Android? | [NEXACRO_TO_ANDROID.md](NEXACRO_TO_ANDROID.md) |
| Server chỉ trả XML X-API, Android cần JSON? | [NEXACRO_XAPI_TO_JSON.md](NEXACRO_XAPI_TO_JSON.md) |
| Nghiệp vụ hủy hàng (등록 → 확정 → 확정취소, 수불, 마감), API, giả định? | [../task-17-18-disposal/README.md](../task-17-18-disposal/README.md) |
| Bản Nexacro 17 "cũ" của hủy hàng + 13 quy tắc và chỗ đã migrate? | [../../nexacro-sample/disposal/README.md](../../nexacro-sample/disposal/README.md) |
| Muốn bài tập luyện thêm? | [MIGRATION_LAB.md](MIGRATION_LAB.md) |
| Muốn học sâu 6 tuần? | [LEARNING_PLAN.md](LEARNING_PLAN.md) |

## Tất cả file

| Nhóm | File | Nội dung |
|---|---|---|
| Lộ trình | [LEARNING_PLAN_2_WEEKS.md](LEARNING_PLAN_2_WEEKS.md) | **Chính.** 10 ngày: đọc code có sẵn + chạy + sửa nhỏ |
| | [LEARNING_PLAN.md](LEARNING_PLAN.md) | 6 tuần, tự làm bài tập (sau khi vào dự án) |
| Làm việc | [HOW_TO_CODE_A_SCREEN.md](HOW_TO_CODE_A_SCREEN.md) | **Chính.** Công thức migrate 1 màn + khung code + review |
| | [PLAIN_JAVA_STYLE.md](PLAIN_JAVA_STYLE.md) | Kiểu code của khách ↔ Retrofit, adapter, lỗi hay gặp |
| | [WORK_WITHOUT_BUILD.md](WORK_WITHOUT_BUILD.md) | VDI: adapter khung, `ProjectCheck`, checklist, test bằng `main` |
| Tra cứu | [NEXACRO_TO_ANDROID.md](NEXACRO_TO_ANDROID.md) | Bảng đối chiếu khái niệm Nexacro → Android |
| | [NEXACRO_XAPI_TO_JSON.md](NEXACRO_XAPI_TO_JSON.md) | X-API (XML) ↔ JSON: gateway, controller JSON, công cụ |
| Luyện tập | [MIGRATION_LAB.md](MIGRATION_LAB.md) | Bài mẫu tra cứu sản phẩm + 8 bài tập |
| Kế hoạch | [MIGRATION_PLAN.md](MIGRATION_PLAN.md) | Giai đoạn, khảo sát, khung app, quy trình 1 màn, VDI, rủi ro |
| Task | [../task-17-18-disposal/README.md](../task-17-18-disposal/README.md) | Task 17–18 hủy hàng: luồng, API, quy tắc |
| Mã mẫu | [../../nexacro-sample/disposal/README.md](../../nexacro-sample/disposal/README.md) | Bản Nexacro 17 cũ của hủy hàng |

## Code đi kèm

| Ở đâu | Là gì |
|---|---|
| `android/.../plain/` | **Kiểu code của khách**: `HttpTask`, `JsonRows`, `HashMapListAdapter`, màn danh sách / chi tiết hủy hàng |
| `android/.../rules/` | Quy tắc Java thuần (test được bằng `main`) |
| `android/devcheck/` | `run.bat` / `run.sh`: `ProjectCheck` + test quy tắc, không cần Gradle |
| `android/.../disposal/` | Cùng chức năng, kiểu Retrofit + RecyclerView (để so sánh) |
| `android/.../migration/` | Bài mẫu của LAB, màn demo X-API → JSON |
| `nexacro-sample/` | Form Nexacro 17: bài mẫu `frm_product_search.xfdl`, bộ hủy hàng `disposal/`, helper `XapiJsonConverter.java.txt` |
| `backend/.../disposal/`, `backend/.../nexacro/` | API hủy hàng, server X-API giả lập, gateway `/api/nx/**` |
