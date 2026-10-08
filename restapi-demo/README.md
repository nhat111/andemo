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

## PDA Finder: tự chọn PDA khi bấm "Tìm PDA" (interceptor + MyBatis)

Mẫu cho yêu cầu "1 nút, hệ thống tự xác định PDA người dùng đang dùng". Viết theo stack giống dự án: **Spring MVC `HandlerInterceptor` + MyBatis (mapper XML, cú pháp Oracle)**. Demo chạy trên **H2 chế độ Oracle** nên không cần Oracle.

### File

| File | Vai trò |
|---|---|
| `src/main/resources/schema.sql` | Bảng `PDA_DEVICE_ACTIVITY`: **1 PDA = 1 dòng** (cửa hàng, người dùng, lần hoạt động gần nhất, lần đăng xuất) |
| `src/main/resources/mapper/DeviceActivityMapper.xml` | `touch` (`MERGE`: có thì UPDATE, chưa có thì INSERT), `logout`, `findCandidates` |
| `pda/DeviceActivityInterceptor.java` | Chạy **sau** mọi `/api/**`: request thành công + có `X-Unique-Id` + đã đăng nhập → ghi "máy vừa hoạt động". **Không sửa từng API** |
| `pda/PdaWebConfig.java` | Đăng ký interceptor 1 lần (`/api/**`, trừ login / logout) |
| `pda/DeviceActivityService.java` | Chặn ghi dồn dập (mỗi máy 1 lần / phút, trong RAM); lỗi ghi **không làm hỏng** API chính |
| `pda/PdaFinderService.java` | Quy tắc chọn máy (xem dưới) |
| `pda/PdaFinderController.java` | `POST /api/pda/find`: PC bấm nút, server tự chọn máy, trả máy đã chọn để PC hiển thị |
| `pda/PdaAuthController.java` | Login / logout **bản demo**: chỉ minh hoạ 2 dòng cần thêm vào login / logout thật |
| `pda/DemoAuth.java` | Lấy người dùng + mã máy từ request. **Chỗ duy nhất phải viết lại theo dự án** (session / JWT) |
| `src/test/.../pda/PdaFinderTest.java` | 14 test: **6 trường hợp của khách**, đăng xuất, interceptor, chặn ghi |

### Quy tắc chọn máy (`application.yml` → `pda.finder`)

Trong các PDA **của cửa hàng người bấm**, hoạt động trong `inactive-days` (7) ngày gần nhất:
1. Ưu tiên máy mà **người bấm** dùng gần nhất (`rule: USER`).
2. Người bấm không có máy → máy dùng gần nhất của **cửa hàng** (`rule: STORE`), nếu `fallback-to-store: true`; ngược lại báo không có.
3. Nhiều máy hoạt động cách máy mới nhất ≤ `near-minutes` (10) phút → **gửi tất cả**.
4. Máy **đã đăng xuất vẫn được chọn** (máy dùng chung thường đăng xuất rồi mới thất lạc). Kết quả có cờ `loggedOut` để PC hiển thị.

SQL chỉ lọc + sắp xếp (máy của người bấm trước, mới nhất trước); phần chọn cuối cùng viết bằng Java cho dễ đọc, dễ đổi quy tắc.

### Thử bằng curl

Demo đọc người dùng từ header `X-Store-Cd` / `X-User-Id` (dự án thật lấy từ session / JWT). PDA gửi thêm `X-Unique-Id`, PC thì không.

```bash
# PDA A (người dùng 001) đăng nhập và dùng app
curl -X POST -H "X-Store-Cd: S001" -H "X-User-Id: 001" -H "X-Unique-Id: PDA-A" localhost:8080/api/auth/login
curl -H "X-Store-Cd: S001" -H "X-User-Id: 001" -H "X-Unique-Id: PDA-A" localhost:8080/api/products
# PC: 001 bấm "Tìm PDA"
curl -X POST -H "X-Store-Cd: S001" -H "X-User-Id: 001" localhost:8080/api/pda/find
# → {"rule":"USER","targets":[{"uniqueId":"PDA-A","lastUserId":"001",...,"loggedOut":false}],"message":"Đã gửi lệnh tìm tới 1 PDA"}
```

### Đưa sang dự án thật

1. **App PDA:** lớp HTTP dùng chung gửi header `X-Unique-Id` trong mọi request (hoặc dùng UNIQUE_ID app đang gửi sẵn).
2. **`DemoAuth.currentUser`:** viết lại theo cách đăng nhập của dự án.
3. **Login / logout thật:** thêm `deviceActivityService.touch(...)` / `logout(...)` như `PdaAuthController`.
4. **Đăng ký interceptor:** Spring Boot → thêm vào `WebMvcConfigurer` có sẵn. Spring MVC XML (eGovFrame) → `dispatcher-servlet.xml`:
   ```xml
   <mvc:interceptors>
       <mvc:interceptor>
           <mvc:mapping path="/api/**"/>
           <mvc:exclude-mapping path="/api/auth/login"/>
           <mvc:exclude-mapping path="/api/auth/logout"/>
           <bean class="kr.co.xxx.pda.DeviceActivityInterceptor"/>
       </mvc:interceptor>
   </mvc:interceptors>
   ```
5. **Gửi lệnh tìm thật** (FCM / polling) ở chỗ `TODO` trong `PdaFinderController`.

### Lưu ý (đã gặp khi làm demo)

| Điểm | Vì sao |
|---|---|
| **Phải loại `/api/auth/logout` khỏi interceptor** | Interceptor chạy **sau** API: nếu không loại, nó `MERGE` lại ngay sau logout và **xoá mất** `LOGOUT_AT` |
| `CAST(#{uniqueId} AS VARCHAR2(64))` trong `MERGE` | H2 không đoán được kiểu tham số trong `SELECT … FROM DUAL` (lỗi `Unknown data type`). Oracle chạy được cả 2 cách |
| Lỗi ghi DB bị "nuốt" (chỉ log `WARN`) | Đúng thiết kế: không làm hỏng API chính. Nhưng sai SQL sẽ **không ai thấy** → có test + theo dõi log `Không ghi được hoạt động PDA` |
| 2 class test dùng 2 Spring context | Mỗi context chạy lại `schema.sql`; dùng chung 1 DB trong RAM sẽ lỗi "table already exists" → test PDA dùng DB riêng (`jdbc:h2:mem:pdatest`) |
| Giờ app server và giờ DB | Mốc 7 ngày tính bằng giờ app server, `LAST_ACTIVE_AT` ghi bằng `SYSDATE` của DB → 2 máy nên đồng bộ giờ (NTP) |
