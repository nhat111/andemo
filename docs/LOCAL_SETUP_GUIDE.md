# Hướng dẫn setup và test PDA Finder trên máy local

> Chạy toàn bộ hệ thống trên máy tính cá nhân: **mock server + web quản lý + app Android trên máy ảo hoặc máy thật**. Không cần Render, không cần Firebase.
> Viết cho Windows + Android Studio (có ghi chú cho macOS). Cập nhật: 2026-09-28

---

## Mục lục

0. [Tổng quan: ai làm gì](#0-tổng-quan-ai-làm-gì)
1. [Chuẩn bị công cụ](#1-chuẩn-bị-công-cụ)
2. [Lấy code và chọn nhánh](#2-lấy-code-và-chọn-nhánh)
3. [Cài và kiểm tra Java](#3-cài-và-kiểm-tra-java)
4. [Tạo máy ảo Android](#4-tạo-máy-ảo-android)
5. [Cấu hình địa chỉ server cho app](#5-cấu-hình-địa-chỉ-server-cho-app)
6. [Chạy mock server](#6-chạy-mock-server)
7. [Chạy app](#7-chạy-app)
8. [Test luồng tìm PDA](#8-test-luồng-tìm-pda)
9. [Các kịch bản test nâng cao](#9-các-kịch-bản-test-nâng-cao)
10. [Test trên máy thật (realme)](#10-test-trên-máy-thật-realme)
11. [Xem log](#11-xem-log)
12. [Chuyển sang backend thật / WebSocket / Render](#12-chuyển-sang-backend-thật--websocket--render)
13. [Xử lý lỗi thường gặp](#13-xử-lý-lỗi-thường-gặp)
14. [Tóm tắt lệnh](#14-tóm-tắt-lệnh)

---

## 0. Tổng quan: ai làm gì

```
┌──────────────────────────┐          ┌───────────────────────────────┐          ┌───────────────────────────┐
│ Trình duyệt trên máy tính│          │ Mock server (máy tính)        │          │ Máy ảo / máy thật (PDA)   │
│ http://localhost:8081/   │          │ java tools\MockPdaAlertServer │          │ App Andemo, login "user"  │
│ login "admin"            │          │                               │          │                           │
│                          │  (1) Tìm │  lưu lệnh, status = SENT      │ (2) hỏi  │ mỗi ~30 giây hỏi server:  │
│ bấm "🔔 Tìm" ───────────►│─────────►│                               │◄─────────│ "có lệnh cho tôi không?"  │
│                          │          │  trả lệnh ───────────────────►│──────────│ (3) nhận lệnh → KÊU       │
│ (5) thấy ⏳ → 🔔 → ✅    │◄─────────│  status = DELIVERED / STOPPED │◄─────────│ (4) báo "đã nhận"/"đã tắt"│
└──────────────────────────┘          └───────────────────────────────┘          └───────────────────────────┘
       REQUESTER (quản lý)                         SERVER                                 PDA
```

- **Requester** (người đi tìm): trang web quản lý, **hoặc** nút "Tìm PDA" trong app khi login bằng `admin`.
- **Server**: bản local dùng **mock server** (một file Java, dữ liệu nằm trong RAM). Bản thật là backend Spring, xem mục 12.
- **PDA** (máy cần tìm): app Andemo đã login. **Máy nào đã login đều là PDA**, kể cả khi login bằng `admin`.
- **Polling**: server **không đẩy** lệnh xuống được, mà phải chờ PDA tự hỏi. Vì vậy sau khi bấm Tìm có thể phải chờ tới ~30 giây.

---

## 1. Chuẩn bị công cụ

| Công cụ | Yêu cầu | Ghi chú |
|---|---|---|
| Git | Bản nào cũng được | Để lấy code |
| Android Studio | Bản mới (2023.x trở lên) | Có sẵn JDK trong thư mục `jbr` |
| Android SDK | Platform **34** | Android Studio tự cài khi mở project; hoặc **Tools → SDK Manager → SDK Platforms → Android 14 (API 34)** |
| JDK | **17 trở lên** | Dùng JDK có sẵn của Android Studio, hoặc cài Temurin 17 (mục 3) |
| Trình duyệt | Chrome / Edge / Firefox | Mở web quản lý |
| Máy thật (không bắt buộc) | Android 7.0+ (ví dụ realme RMX1851, Android 11) | Mục 10 |

---

## 2. Lấy code và chọn nhánh

### 2.1 Lấy code

```bash
git clone https://github.com/nhat111/andemo.git
cd andemo
```

Đã có sẵn repo thì cập nhật:
```bash
cd andemo
git fetch origin
```

### 2.2 Chọn nhánh

| Nhánh | Dùng khi | Server đi kèm |
|---|---|---|
| `claude/pda-finder-polling` | **Học và test polling trên máy local** (hướng dẫn này) | Mock server `tools/MockPdaAlertServer.java` |
| `claude/pda-finder-websocket` | Test với backend Spring thật, WebSocket, deploy Render | Backend Spring trong `backend/` (mục 12) |

```bash
git checkout claude/pda-finder-polling
git pull
```

**App và server phải cùng nhánh.** Ví dụ không build app từ nhánh websocket rồi chạy với mock server của nhánh polling.

Kiểm tra đang ở nhánh nào:
```bash
git branch
```
Dòng có dấu `*` là nhánh hiện tại.

### 2.3 Mở project trong Android Studio

**File → Open** → chọn thư mục **`andemo/android`** (không phải `andemo`). Chờ Gradle Sync xong (thanh tiến trình ở góc dưới bên phải).

---

## 3. Cài và kiểm tra Java

Mock server là một file Java nên cần lệnh `java` chạy được trong terminal.

### 3.1 Kiểm tra

Mở terminal của Android Studio (tab **Terminal** ở dưới cùng; mặc định là PowerShell), gõ:
```powershell
java -version
```
Thấy `version "17..."` hoặc cao hơn là được, chuyển sang mục 4.

### 3.2 Nếu báo `'java' is not recognized...`

**Cách A (tạm thời, dùng ngay):** trỏ tới JDK có sẵn của Android Studio, chỉ có tác dụng trong tab terminal đang mở:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
java -version
```
Android Studio cài chỗ khác thì thay đường dẫn cho đúng (tìm thư mục có `jbr\bin\java.exe`).

**Cách B (lâu dài, khuyên dùng):**
1. **Start** → gõ `environment variables` → **Edit the system environment variables** → **Environment Variables…**
2. Mục **System variables** (hoặc User variables) → **New…**:
   - Variable name: `JAVA_HOME`
   - Variable value: `C:\Program Files\Android\Android Studio\jbr` (hoặc thư mục JDK bro tự cài, ví dụ `C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot`)
3. Chọn biến **Path** → **Edit…** → **New** → gõ `%JAVA_HOME%\bin` → OK hết các hộp thoại.
4. **Quan trọng: thoát hẳn Android Studio** (**File → Exit**) rồi mở lại. Terminal trong Android Studio chỉ nhận biến môi trường mới khi Android Studio khởi động lại; đóng tab terminal thôi là không đủ. Nếu mở Android Studio bằng JetBrains Toolbox thì thoát cả Toolbox.
5. Mở lại tab Terminal → `java -version`.

**Hoặc cài JDK riêng:** tải **Temurin 17** (file `.msi`, Windows x64) ở trang adoptium.net; lúc cài tick **"Add to PATH"** và **"Set JAVA_HOME variable"**; sau đó làm lại bước 4 ở trên.

### 3.3 Lưu ý

- Gõ `java -version` ở ổ C thì được mà trong thư mục dự án lại không: gần như chắc chắn là **terminal đang mở từ trước khi sửa biến môi trường**. Mở lại terminal, hoặc khởi động lại Android Studio.
- cmd: dự án ở ổ khác thì dùng `cd /d D:\andemo` (thiếu `/d` là không đổi ổ).
- macOS: `brew install --cask temurin@17`, hoặc dùng JDK của Android Studio tại `/Applications/Android Studio.app/Contents/jbr/Contents/Home`.

---

## 4. Tạo máy ảo Android

1. **View → Tool Windows → Device Manager** (hoặc icon điện thoại ở thanh bên phải).
2. **Create Virtual Device** (dấu **+**).
3. Chọn phần cứng: **Phone → Pixel 6** (hoặc bất kỳ). Muốn giống PDA hơn: **New Hardware Profile** → màn hình 5.0", độ phân giải 720 × 1280, RAM 3072 MB, tick **Has hardware keyboard** (để giả lập scanner kiểu bàn phím sau này).
4. Chọn system image: **API 34 (Android 14)**, bản **"Google APIs"**. Nếu chưa tải thì bấm biểu tượng tải bên cạnh.
   - Muốn giống máy realme của bro (Android 11) thì tạo thêm một máy **API 30**.
5. **Finish** → bấm ▶ để bật máy ảo.

Máy ảo **API 33 trở lên** cần cấp quyền thông báo cho app (mục 7.2).

---

## 5. Cấu hình địa chỉ server cho app

### 5.1 App lấy địa chỉ server từ đâu

Trong `android/app/build.gradle`:
```groovy
buildConfigField "String", "API_BASE_URL",
        "\"${project.findProperty('apiBaseUrl') ?: 'https://andemo.onrender.com/'}\""
```
- Gradle tìm thuộc tính **`apiBaseUrl`**. Không thấy thì dùng giá trị sau `?:`, tức **server Render**.
- Lúc build, giá trị này được ghi vào class `BuildConfig`, và `ApiClient.java` đọc `BuildConfig.API_BASE_URL`.
- URL **được chốt lúc build**: đổi URL xong phải build lại (Sync + Run).

**Bấm nút Run mà chưa cấu hình gì thì app sẽ gọi Render, không phải mock server.**

### 5.2 Khai báo `apiBaseUrl`

Chọn **một** trong hai file:

| File | Ưu điểm |
|---|---|
| `C:\Users\<tên user>\.gradle\gradle.properties` | **Khuyên dùng.** Nằm ngoài repo nên không bao giờ commit nhầm. Tạo file mới nếu chưa có |
| `andemo\android\gradle.properties` | Nhanh, nhưng **nhớ không commit** dòng này |

Thêm **một** dòng, tùy nơi chạy app:
```properties
# Máy ảo: 10.0.2.2 là địa chỉ để máy ảo gọi tới máy tính đang chạy nó
apiBaseUrl=http://10.0.2.2:8081/

# Máy thật: IP của máy tính trong mạng Wi-Fi (mục 10.3)
# apiBaseUrl=http://192.168.1.10:8081/
```

Dấu `/` ở cuối là **bắt buộc**.

### 5.3 Sync và kiểm tra

1. Bấm **Sync Now** trên thanh vàng, hoặc **File → Sync Project with Gradle Files**.
2. **Build → Make Project**.
3. Mở file `android/app/build/generated/source/buildConfig/debug/com/example/andemo/BuildConfig.java`, phải thấy:
   ```java
   public static final String API_BASE_URL = "http://10.0.2.2:8081/";
   ```

Muốn quay lại dùng Render: xóa dòng `apiBaseUrl` (hoặc thêm `#` ở đầu), rồi Sync lại.

---

## 6. Chạy mock server

### 6.1 Chạy

Trong terminal của Android Studio, **đứng ở thư mục gốc repo** (thư mục có `tools`, `backend`, `android`):
```powershell
cd ..        # nếu terminal đang ở andemo\android
dir          # phải thấy tools, backend, android, docs
java tools\MockPdaAlertServer.java
```

Chạy đúng sẽ thấy:
```
Mock PDA alert server on http://localhost:8081
Web: http://localhost:8081/  (login: admin / any password)
Emulator: -PapiBaseUrl=http://10.0.2.2:8081/ | Real device: -PapiBaseUrl=http://<LAN IP of this PC>:8081/
```

- **Giữ tab terminal này mở.** Tắt tab là tắt server.
- Mở thêm tab terminal thứ hai (dấu **+**) cho các lệnh khác.
- Dừng server: bấm `Ctrl + C` trong tab đó.
- Cổng 8081 bị chiếm thì chạy cổng khác, ví dụ `java tools\MockPdaAlertServer.java 9000`, và đổi `apiBaseUrl` sang cổng 9000.

### 6.2 Tài khoản trên mock server

| Tài khoản | Mật khẩu | Role | Dùng cho |
|---|---|---|---|
| `admin` | bất kỳ | ADMIN | Web quản lý, nút "Tìm PDA" trong app |
| tên khác (ví dụ `user`) | bất kỳ | USER | PDA thông thường |

Dữ liệu nằm trong RAM: **tắt mock server là mất hết**. Mở lại thì app cần logout và login lại.

### 6.3 Mở web quản lý

Trình duyệt trên máy tính → **`http://localhost:8081/`** → login `admin`.

| Khu vực | Nội dung |
|---|---|
| 1. Chọn PDA cần tìm | Danh sách PDA đã liên lạc với server; ô Nội dung, Mã cửa hàng, Hiệu lực lệnh (60/90/120 giây) |
| 2. Theo dõi lệnh | Các bước ⏳ → 🔔 → ✅ của lệnh vừa gửi |
| 3. Lịch sử tìm kiếm | Ai tìm, lúc nào, máy nào, kết quả |
| Đối chiếu requirement | Requirement nào đã có, còn thiếu gì |

Chưa có app nào login thì danh sách PDA trống; đó là bình thường.

---

## 7. Chạy app

### 7.1 Run

1. Chọn máy ảo ở ô thiết bị trên thanh công cụ.
2. Bấm **Run ▶** (hoặc `Shift + F10`).

Hoặc dùng lệnh (terminal thứ hai, trong thư mục `android`):
```powershell
.\gradlew installDebug -PapiBaseUrl=http://10.0.2.2:8081/
```
`-PapiBaseUrl` trên dòng lệnh được ưu tiên hơn giá trị trong `gradle.properties`.

### 7.2 Cấp quyền thông báo (máy Android 13 trở lên)

App **chưa tự xin quyền này** (bug A3 trong `ANDROID_FIX_TRACKER.md`). Thiếu quyền thì máy vẫn kêu nhưng **không có nút tắt chuông trên thanh thông báo**.

Cách 1: trên máy ảo vào **Settings → Apps → Andemo → Notifications → bật**.

Cách 2: dùng lệnh (terminal thứ hai):
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell pm grant com.example.andemo android.permission.POST_NOTIFICATIONS
```
Nếu `adb` đã có trong PATH thì chỉ cần: `adb shell pm grant com.example.andemo android.permission.POST_NOTIFICATIONS`.

### 7.3 Login

- Muốn máy ảo **chỉ là PDA**: login `user`.
- Muốn máy ảo **vừa là PDA vừa là requester**: login `admin` (có thêm nút "Tìm PDA").
- App đã login sẵn từ trước (ví dụ lúc còn gọi Render): **Logout rồi login lại**, vì token cũ không dùng được với mock server.

Sau khi login, thanh thông báo có dòng **"Đang chờ lệnh tìm PDA"** (thu gọn ở cuối danh sách; không có icon trên thanh trạng thái). Đó là foreground service polling đang chạy.

---

## 8. Test luồng tìm PDA

### 8.1 Cách 1 (khuyên dùng): web làm requester, máy ảo làm PDA

1. Máy ảo login `user` (hoặc `admin`).
2. Xem terminal mock server:
   ```
   10:15:02 GET /api/pda/alerts/pending?deviceId=a1b2c3d4...&deviceName=Google%20sdk_gphone64_x86_64
   10:15:02 >>> New PDA: a1b2c3d4... (Google sdk_gphone64_x86_64)
   10:15:32 GET /api/pda/alerts/pending?...
   10:16:02 GET /api/pda/alerts/pending?...
   ```
   **Dòng `GET /pending` lặp lại mỗi ~30 giây chính là polling.**
3. Web `http://localhost:8081/`: trong vòng 10 giây, máy ảo xuất hiện với badge **Online**.
4. Bấm **🔔 Tìm** ở dòng máy ảo.
5. Theo dõi:

| Thời điểm | Web | Máy ảo | Terminal mock server |
|---|---|---|---|
| Ngay sau khi bấm | ⏳ Đang chờ PDA nhận lệnh | chưa có gì | `>>> Created REQ-... by admin for ...` |
| Lần poll kế tiếp (≤ 30 giây) | 🔔 PDA đang đổ chuông | **Kêu** (âm thanh qua loa máy tính), hiện popup "PDA đang được tìm kiếm" | `>>> REQ-... is now DELIVERED` |
| Bấm **"Dừng Alert"** trên máy ảo | ✅ Đã tìm thấy PDA | Tắt chuông | `>>> REQ-... is now STOPPED_BY_USER` |
| Không bấm gì, sau ~60 giây | ⏱ Chuông đã kêu hết thời gian | Tự tắt chuông | `>>> REQ-... is now TIMED_OUT` |

6. Bảng **Lịch sử** trên web có thêm một dòng: giờ gửi, tên máy, người tìm (`admin`), mã cửa hàng, "PDA nhận sau x giây", kết quả.

### 8.2 Cách 2: chỉ dùng máy ảo (login `admin`)

1. Máy ảo login `admin` → màn hình chính có nút **"Tìm PDA"**.
2. Bấm **Tìm PDA** → danh sách có dòng **"… (máy này)"** → bấm vào → **Tìm**.
3. Màn hình hiện ⏳; trong ≤ 30 giây máy tự kêu, popup đè lên màn hình.
4. Bấm **"Dừng Alert"** → quay lại màn hình "Tìm PDA" → thấy ✅.

### 8.3 Cách 3: không cần app (chỉ để hiểu API)

```powershell
# Giả lập 1 PDA hỏi lệnh (PDA "Test" sẽ xuất hiện trên web)
curl.exe "http://localhost:8081/api/pda/alerts/pending?deviceId=test1&deviceName=Test"

# Tạo lệnh tìm PDA test1
curl.exe -X POST "http://localhost:8081/api/pda/alerts?deviceId=test1&message=Tim%20may"

# PDA test1 hỏi lại → nhận được lệnh
curl.exe "http://localhost:8081/api/pda/alerts/pending?deviceId=test1"

# PDA báo đã nhận (thay REQ-xxxx bằng requestId ở trên).
# Gửi JSON trong PowerShell dùng Invoke-RestMethod (curl.exe dễ lỗi dấu ngoặc kép)
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/pda/alerts/REQ-xxxx/ack" `
  -ContentType "application/json" -Body '{"deviceId":"test1","status":"DELIVERED"}'

# Xem danh sách PDA và lịch sử
curl.exe http://localhost:8081/api/pda/devices
curl.exe http://localhost:8081/api/pda/alerts
```
Trong PowerShell dùng `curl.exe` (vì `curl` là tên gọi tắt của một lệnh khác trong PowerShell).

---

## 9. Các kịch bản test nâng cao

Đường dẫn adb (dùng trong các lệnh bên dưới):
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
```

| # | Kịch bản | Cách làm | Mong đợi | Liên quan |
|---|---|---|---|---|
| 1 | App ở background | Bấm Home trên máy ảo → bấm Tìm trên web | Vẫn kêu. Popup có thể **không** hiện (bị chặn mở màn hình từ background); tắt chuông bằng nút "Dừng Alert" trên thanh thông báo | Bug A1, A2 |
| 2 | Khóa màn hình | Máy ảo: Settings → Security → đặt PIN → khóa màn hình → bấm Tìm | Xem popup có hiện trên màn hình khóa không | Bug A1 |
| 3 | 2 lệnh liên tiếp | Bấm Tìm 2 lần cách nhau 10 giây | Chỉ 1 tiếng chuông (lệnh sau thay lệnh trước) | Fix A4 |
| 4 | Mất mạng rồi có lại | Máy ảo bật **Airplane mode** → bấm Tìm trên web → 1 phút sau tắt Airplane mode | Kêu gần như ngay khi có mạng lại (app poll ngay khi mạng trở lại) | `NetworkCallback` |
| 5 | Lệnh hết hạn | Chọn **Hiệu lực lệnh = 60 giây** → bật Airplane mode → bấm Tìm → đợi 2 phút → tắt Airplane mode | Không kêu; web hiện ❌ Hết hạn | TTL (requirement §3.1) |
| 6 | PDA offline trên web | Airplane mode khoảng 2 phút | Web hiện badge **Offline**; bấm Tìm sẽ hỏi xác nhận | Requirement §3.6 |
| 7 | Volume được trả lại | Chỉnh volume báo thức của máy ảo xuống thấp → Tìm → Dừng | Volume trở về mức thấp như trước | Fix A6 |
| 8 | Doze (máy ngủ sâu) | Khóa màn hình → `& $adb shell dumpsys deviceidle force-idle` → bấm Tìm → **đo thời gian tới khi kêu** | Chậm hơn nhiều so với 30 giây | Mục 4 `PDA_POLLING_DESIGN.md` |
| 9 | Logout | Logout trên app | Dòng "Đang chờ lệnh tìm PDA" biến mất; terminal hết thấy `GET /pending` | Vòng đời service |
| 10 | Khởi động lại máy | Tắt và bật lại máy ảo, **không mở app** → bấm Tìm | Vẫn kêu (`BootReceiver` tự chạy lại polling) | `BootReceiver` |

Sau kịch bản 8, trả máy về bình thường:
```powershell
& $adb shell dumpsys deviceidle unforce
```

---

## 10. Test trên máy thật (realme)

### 10.1 Bật chế độ nhà phát triển và USB debugging

1. **Settings → About phone → Version** (ô "Version", dòng dưới ghi "Baseband & kernel").
2. Bấm **Build number** **7 lần** liên tiếp → nhập mã PIN nếu được hỏi → thấy "You are now in developer mode".
3. Quay lại **Settings → Additional settings → Developer options** → bật **USB debugging**.
4. Cắm cáp vào máy tính → trên điện thoại chọn **Allow** (tick "Always allow from this computer").
5. Kiểm tra: `& $adb devices` → dòng cuối phải là `... device`. Nếu là `unauthorized` thì mở khóa điện thoại và bấm Allow lại.

### 10.2 Lỗi cài app riêng của realme/OPPO

Nếu Android Studio báo `INSTALL_FAILED_USER_RESTRICTED`:
- Lúc bấm Run, xem điện thoại có hỏi cho phép cài không → bấm **Install / Allow** ngay (hộp thoại tự hủy sau vài giây).
- Hoặc trong **Developer options**, bật **"Install via USB"** / **"Disable permission monitoring"** (có thể phải đăng nhập tài khoản realme).

### 10.3 Địa chỉ server cho máy thật

Máy thật **không dùng được** `10.0.2.2`. Cần IP của máy tính trong mạng Wi-Fi:
- Windows: `ipconfig` → dòng **IPv4 Address** của card Wi-Fi (ví dụ `192.168.1.10`).
- macOS: `ipconfig getifaddr en0`.

Đổi dòng trong `gradle.properties`:
```properties
apiBaseUrl=http://192.168.1.10:8081/
```
→ **Sync** → **Run**. Điện thoại và máy tính phải **cùng một mạng Wi-Fi**.

### 10.4 Mở firewall Windows cho cổng 8081

Lần đầu chạy mock server, Windows có thể hỏi cho phép Java truy cập mạng → chọn **Allow** (tick cả Private network).

Hoặc tự mở cổng: mở **PowerShell bằng quyền Administrator** rồi chạy:
```powershell
New-NetFirewallRule -DisplayName "Andemo mock 8081" -Direction Inbound -Protocol TCP -LocalPort 8081 -Action Allow -Profile Private
```

Kiểm tra từ điện thoại: mở Chrome trên điện thoại → `http://192.168.1.10:8081/` → phải thấy trang đăng nhập PDA Finder.

Wi-Fi công cộng hoặc mạng khách (guest) thường chặn các máy nói chuyện với nhau; khi đó dùng Wi-Fi nhà hoặc phát hotspot từ điện thoại.

### 10.5 Cài đặt pin trên realme

Để polling không bị realme kill khi tắt màn hình: **Settings → App management → Andemo** → bật **Auto launch**, cho phép **chạy nền**, tắt **tối ưu pin**. Khi test **không vuốt "Xóa tất cả"** trong màn hình đa nhiệm (trên realme thao tác này có thể dừng hẳn app).

---

## 11. Xem log

### 11.1 Logcat trong Android Studio

**View → Tool Windows → Logcat** → chọn đúng thiết bị → ô lọc gõ:
```
package:mine (tag:AlertPoller | tag:AlertDispatcher | tag:PdaAlertService | tag:PdaPollingService)
```

| Log | Ý nghĩa |
|---|---|
| `AlertPoller: Poll ok, pending alerts: 0` | Hỏi server thành công, chưa có lệnh |
| `AlertPoller: Poll failed: ...` | Không gọi được server (sai URL, mất mạng, server chưa chạy) |
| `AlertDispatcher: Started PdaAlertService for requestId=...` | Nhận lệnh mới, bắt đầu kêu |
| `AlertDispatcher: Alert already handled` | Lệnh đã xử lý rồi, không kêu lại |
| `AlertDispatcher: Cannot start PdaAlertService, showing fallback notification` | Android chặn service từ background; app chuyển sang notification dự phòng |
| `PdaPollingService: Network available, polling now` | Có mạng lại, poll ngay |

Muốn xem URL app đang gọi: lọc chữ `okhttp`, sẽ thấy các dòng `--> GET http://10.0.2.2:8081/api/pda/alerts/pending...`.

### 11.2 Log mock server

| Log | Ý nghĩa |
|---|---|
| `GET /api/pda/alerts/pending?...` | Một lần poll của PDA |
| `>>> New PDA: ...` | PDA liên lạc lần đầu |
| `>>> Created REQ-... by admin for ...` | Requester vừa gửi lệnh |
| `>>> REQ-... is now DELIVERED` | PDA đã nhận lệnh, đang kêu |
| `>>> REQ-... is now STOPPED_BY_USER` | Có người bấm "Dừng Alert" |
| `>>> REQ-... is now TIMED_OUT` | Chuông tự tắt sau 60 giây |

---

## 12. Chuyển sang backend thật / WebSocket / Render

| Muốn | Làm gì | Tài liệu |
|---|---|---|
| Backend Spring thật chạy trên máy (thay mock) | `git checkout claude/pda-finder-websocket` → `cd backend` → `mvn spring-boot:run` (**cần cài Maven**; hoặc mở thư mục `backend` bằng IntelliJ IDEA và chạy `AndemoApplication`) → cổng **8080**, tài khoản `admin`/`user`, mật khẩu `123456` → `apiBaseUrl=http://10.0.2.2:8080/` | `PDA_WEBSOCKET_DESIGN.md` mục 5 |
| App dùng WebSocket (kêu gần như ngay lập tức) | Nhánh websocket, build mặc định là WebSocket | `PDA_WEBSOCKET_DESIGN.md` |
| App nhánh websocket nhưng vẫn chạy polling | Thêm `pdaChannel=polling` vào `gradle.properties` (hoặc `-PpdaChannel=polling`) | |
| Deploy lên Render | Nhánh websocket | `DEPLOY_RENDER.md` |

Web quản lý của backend Spring nằm ở `http://localhost:8080/pda-finder.html`.

---

## 13. Xử lý lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách sửa |
|---|---|---|
| `'java' is not recognized…` | Chưa có Java trong PATH | Mục 3.2 |
| `java -version` chạy được ở ổ C nhưng không chạy được trong thư mục dự án | Terminal mở từ trước khi sửa biến môi trường | Thoát hẳn Android Studio (File → Exit) rồi mở lại |
| `gradlew` báo `JAVA_HOME is not set` | Thiếu biến `JAVA_HOME` | Mục 3.2 cách B, hoặc chạy trong terminal của Android Studio sau khi đã đặt |
| Web báo `Web page not found` | Mock server không chạy từ thư mục gốc repo | `cd` về thư mục có `tools`, `backend` rồi chạy lại |
| `Address already in use` | Cổng 8081 bị chiếm (có thể mock server cũ vẫn đang chạy) | Tắt tab terminal cũ, hoặc chạy cổng khác (mục 6.1) |
| App vẫn gọi Render | Bấm Run nhưng chưa khai báo `apiBaseUrl`, hoặc chưa Sync | Mục 5.2, 5.3 |
| App báo "Lỗi kết nối" | Sai URL, thiếu `/` cuối, mock server chưa chạy; máy thật: khác Wi-Fi hoặc firewall chặn | Mục 5, 6, 10.3, 10.4 |
| Login được nhưng web không thấy máy | App login từ trước khi đổi server (token cũ) | Logout rồi login lại |
| Web thấy máy nhưng bấm Tìm cứ ⏳ mãi | App không còn poll (xem terminal còn `GET /pending` không) | Mở lại app; kiểm tra app không bị dừng trong "Active apps" |
| Web 🔔 nhưng không nghe tiếng | Tắt tiếng máy tính hoặc máy ảo | Bật loa máy tính; trên máy ảo tăng volume |
| Máy kêu nhưng không có nút tắt trên thanh thông báo | Android 13+ chưa cấp quyền thông báo | Mục 7.2 |
| Kêu nhưng không thấy popup | App ở background hoặc máy đang khóa (bug A1/A2) | Tắt bằng nút trên thanh thông báo; mở app để thấy popup |
| `INSTALL_FAILED_USER_RESTRICTED` (realme) | Chặn cài qua USB | Mục 10.2 |
| `adb devices` không thấy máy thật | Chưa bật USB debugging hoặc chưa bấm Allow | Mục 10.1 |
| Tắt mock server mở lại thì app lỗi | Mock mất dữ liệu khi tắt | Logout rồi login lại trên app |

---

## 14. Tóm tắt lệnh

```powershell
# --- 1 lần ---
git checkout claude/pda-finder-polling
# C:\Users\<user>\.gradle\gradle.properties:
#   apiBaseUrl=http://10.0.2.2:8081/        (máy ảo)
#   apiBaseUrl=http://<IP máy tính>:8081/   (máy thật)
# Android Studio: Sync Project with Gradle Files

# --- Mỗi lần test ---
# Tab terminal 1 (thư mục gốc repo):
java tools\MockPdaAlertServer.java

# Android Studio: chọn máy → Run ▶ → login "user" (hoặc "admin")
# Trình duyệt: http://localhost:8081/ → login "admin" → 🔔 Tìm

# --- Tiện ích ---
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell pm grant com.example.andemo android.permission.POST_NOTIFICATIONS   # Android 13+
& $adb shell dumpsys deviceidle force-idle      # giả lập Doze (tắt màn hình trước)
& $adb shell dumpsys deviceidle unforce         # trả lại bình thường
& $adb logcat -s AlertPoller AlertDispatcher PdaAlertService PdaPollingService
```

Học tiếp: [ANDROID_LEARNING_PLAN.md](ANDROID_LEARNING_PLAN.md) (buổi 7–8 cho service và polling). Thiết kế polling chi tiết: [PDA_POLLING_DESIGN.md](PDA_POLLING_DESIGN.md).
