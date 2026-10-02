# restapi-demo: project REST API mẫu để tập build / run ở nhà

> 📚 [Mục lục tài liệu](../docs/nexacro-migration/README.md)

Project Spring Boot nhỏ, **cấu trúc giống project REST API của dự án** (Gradle 7, Java 8, `WEB-INF` ở gốc, `resources-local/dev/ops`, `local.properties`, đóng gói `.war`).
Code ở đây là code mẫu, không lấy từ project của khách. Dùng để tập: mở project, chọn JDK, chọn môi trường, build, run, đọc lỗi.

Đã kiểm tra: `gradlew clean build` qua (kèm test), chạy file `.war` gọi được API, đổi môi trường bằng `-Pprofile` và `local.properties` đúng.

## Cấu trúc

```
restapi-demo/
├── gradle/wrapper/            Gradle wrapper (tự tải Gradle 7.6.4 lần đầu chạy)
├── gradlew, gradlew.bat       Lệnh build (Mac/Linux, Windows)
├── settings.gradle            Tên project
├── build.gradle               Plugin, thư viện, CHỌN MÔI TRƯỜNG (đọc phần "profile")
├── local.properties.example   Mẫu cấu hình riêng máy → copy thành local.properties (không commit)
├── WEB-INF/web.xml            Giống dự án cũ: được chép vào file .war
└── src/
    ├── main/java/com/example/restdemo/
    │   ├── RestDemoApplication.java   main class (bấm Run ở đây)
    │   ├── StartupLog.java            in ra môi trường đang chạy khi khởi động
    │   └── web/                       EnvController, ProductController (API)
    ├── main/resources/application.yml cấu hình chung
    ├── main/resources-local/env.yml   cấu hình máy dev  ← mặc định
    ├── main/resources-dev/env.yml     cấu hình server dev
    ├── main/resources-ops/env.yml     cấu hình production (giả lập)
    └── test/...                       test API
```

Chọn môi trường: `build.gradle` lấy `src/main/resources` + **đúng 1** thư mục `src/main/resources-<profile>`.
`profile` lấy theo thứ tự: `-Pprofile=xxx` khi chạy lệnh → `profile=xxx` trong `local.properties` → mặc định `local`.

## Cần có

- **JDK 17** (khuyên dùng ở nhà) hoặc **JDK 8** (giống máy khách). Gradle 7.6 **chưa hỗ trợ chính thức** JDK 21: có project vẫn chạy, có project lỗi, nên đừng dùng 21.
- Internet (lần đầu tải Gradle và thư viện từ Maven Central).

## Chạy bằng Android Studio

1. **File → Open** → chọn thư mục `restapi-demo` (thư mục có `settings.gradle`).
2. *Settings → Build, Execution, Deployment → Build Tools → Gradle → **Gradle JDK*** → chọn JDK 17. Chưa có thì chọn *Download JDK…* → bản 17.
3. Đợi Gradle sync xong (thanh dưới cùng hết chạy, tab **Build** không báo lỗi).
4. Tab **Gradle** (bên phải) → `restapi-demo` → **Tasks → application → bootRun** (bấm đúp).
5. Thấy log `Started RestDemoApplication` và `==> Môi trường: local` là chạy xong.

## Chạy bằng VS Code

1. Cài extension **Extension Pack for Java** và **Gradle for Java** (Microsoft).
2. **File → Open Folder** → `restapi-demo`.
3. Chỉ JDK 17 cho Gradle: `Ctrl+,` → tìm `java.import.gradle.java.home` → điền đường dẫn JDK 17.
4. Biểu tượng **Gradle** (thanh bên trái) → `restapi-demo` → **Tasks → application → bootRun**.
   Hoặc mở `RestDemoApplication.java` → bấm **Run** ngay trên hàm `main`.

## Chạy bằng dòng lệnh

```powershell
# Windows PowerShell, ở thư mục restapi-demo
$env:JAVA_HOME = "C:\đường\dẫn\jdk-17"
.\gradlew.bat bootRun                    # môi trường local
.\gradlew.bat bootRun -Pprofile=dev      # môi trường dev
.\gradlew.bat clean build                # build + test → build\libs\restapi-demo-0.0.1.war
java -jar build\libs\restapi-demo-0.0.1.war
```

Mac / Linux: thay `.\gradlew.bat` bằng `./gradlew`, `$env:JAVA_HOME = ...` bằng `export JAVA_HOME=...`.

## Thử API

Mở trình duyệt hoặc dùng `curl`:

| Gọi | Kết quả |
|---|---|
| `GET http://localhost:8080/api/env` | `{"env":"local", ...}`: môi trường đang chạy |
| `GET http://localhost:8080/api/products?keyword=kim` | danh sách sản phẩm |
| `GET http://localhost:8080/api/products/999` | 404 + `{"message": ...}` |
| `POST http://localhost:8080/api/products` body `{"code":"1","name":"Bánh","price":1000}` | 201; gửi lại lần 2 → 409 |

```bash
curl -H "Content-Type: application/json" -d "{\"code\":\"1\",\"name\":\"Banh\",\"price\":1000}" http://localhost:8080/api/products
```

## Bài tập: tự gây lỗi rồi sửa (giống lỗi gặp trong VDI)

| # | Làm | Thấy | Học được |
|---|---|---|---|
| 1 | Copy `local.properties.example` thành `local.properties`, sửa `profile=dev`, chạy lại | `/api/env` trả `dev` | Môi trường lấy từ `local.properties` |
| 2 | Chạy `.\gradlew.bat bootRun -Pprofile=prod` | `profile không hợp lệ: 'prod'` | Tên môi trường phải đúng |
| 3 | `-Pprofile=ops` | Log cảnh báo `ĐANG CHẠY CẤU HÌNH OPS` | Không chạy cấu hình production trên máy dev |
| 4 | Xoá tạm `src/main/resources-local/env.yml`, chạy | Lỗi khởi động `Config data resource ... [env.yml] ... cannot be found` | Thiếu cấu hình môi trường → app không lên |
| 5 | Trong IDE, chạy `main` khi Gradle sync **chưa** xong / đang lỗi | `Could not find or load main class` (`기본 클래스 ... 찾거나 로드할 수 없습니다`) | Lỗi bro gặp trong VDI: chưa build được (thường do chưa tải được thư viện) |
| 6 | Chạy 2 lần `bootRun` cùng lúc | `Port 8080 was already in use` | Tắt bản đang chạy hoặc đổi `server.port` |
| 7 | Đổi `mavenCentral()` thành một URL Nexus không tới được | `Could not resolve ...` / `timed out` | Giống VDI khi tường lửa chưa mở |

Nhớ trả lại như cũ sau mỗi bài.

## Đối chiếu với project trong VDI

| Ở nhà (demo) | Trong VDI |
|---|---|
| `mavenCentral()` | Nexus nội bộ `.../maven-public/` (cần mở tường lửa, có thể cần tài khoản) |
| Gradle wrapper tải từ `services.gradle.org` | `distributionUrl` có thể là file zip nằm sẵn trong project |
| JDK 17 | JDK 8 (`openjdk-1.8.0_332`) |
| `profile` / `local.properties` | Xem `build.gradle` dự án dùng tên biến gì (tìm chữ `resources-`) |
