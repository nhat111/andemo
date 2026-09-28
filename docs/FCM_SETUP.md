# PDA Finder qua FCM: setup và test

> Nhánh: `claude/pda-finder-fcm` (tách từ `claude/pda-finder-websocket`, có đủ polling + WebSocket + FCM).
> Cần backend Spring (local hoặc Render). Mock server `tools/MockPdaAlertServer.java` **không** hỗ trợ FCM.
> Cập nhật: 2026-09-28

---

## 1. Ba cách nhận lệnh trên cùng một nhánh

Chọn lúc build bằng 1 dòng trong `gradle.properties` (hoặc `-PpdaChannel=...`):

```properties
pdaChannel=polling     # hỏi server mỗi ~30 giây
pdaChannel=websocket   # giữ kết nối, server đẩy lệnh ngay (mặc định)
pdaChannel=fcm         # Google đẩy lệnh qua Firebase, app không chạy nền
```

| | Polling | WebSocket | FCM |
|---|---|---|---|
| Độ trễ khi máy thức | ≤ 30 giây | ~1 giây | Vài giây |
| App bị kill (không phải force-stop) | ❌ tới khi service chạy lại | ❌ tới khi service chạy lại | ✅ Google Play services đánh thức app |
| Máy ngủ sâu (Doze) | Vài phút | Tùy mạng | ✅ Message high priority được giao ngay |
| Notification thường trực | Có | Có | **Không** |
| Cần Google Play services + mạng ra được Google | Không | Không | **Có** |
| Cần setup thêm | Không | Không | Firebase project (mục 2–4) |

Cả 3 cách dùng chung server, web quản lý, màn hình "Tìm PDA" trong app, và phần chống trùng / chuông / ack. Phân tích đầy đủ: [PDA_FINDER_MECHANISM_OPTIONS.md](PDA_FINDER_MECHANISM_OPTIONS.md).

### Luồng FCM

```
Web (admin) ── POST /api/pda/alerts ──► Backend: lưu lệnh
                                         │ gửi data message (priority HIGH, TTL = hiệu lực lệnh)
                                         ▼
                                    Firebase Cloud Messaging ──► Google Play services trên PDA
                                                                    │ đánh thức app (kể cả khi app bị kill)
                                                                    ▼
                                                    MyFirebaseMessagingService.onMessageReceived
                                                    → AlertDispatcher → PdaAlertService (kêu)
                                                    → POST /ack DELIVERED ──► web: 🔔
```

---

## 2. Tạo Firebase project (làm 1 lần)

Giao diện Firebase Console có thể thay đổi theo thời gian; tên menu dưới đây là tên tại thời điểm viết.

1. Vào **console.firebase.google.com** → đăng nhập tài khoản Google → **Create a project** (hoặc "Add project").
2. Đặt tên, ví dụ `andemo-pda-finder`. Google Analytics: không cần, có thể tắt.
3. Chờ tạo xong → vào project.

---

## 3. Phía Android: `google-services.json`

1. Trong project Firebase: **Project settings** (bánh răng) → **Your apps** → **Add app** → chọn **Android**.
2. **Android package name**: `com.example.andemo` (phải đúng y hệt). Các ô khác bỏ trống được.
3. **Register app** → **Download google-services.json**.
4. Đặt file vào **`andemo/android/app/google-services.json`**.
   - File đã có trong `.gitignore`, **không commit**.
   - Có file này thì Gradle tự bật plugin Firebase; không có thì app vẫn build, chỉ là không có FCM.
5. Các bước "Add Firebase SDK" trong wizard: **bỏ qua**, project đã cấu hình sẵn.
6. Trong `gradle.properties`:
   ```properties
   apiBaseUrl=https://<service>.onrender.com/   # hoặc http://10.0.2.2:8080/ nếu chạy backend Spring trên máy
   pdaChannel=fcm
   ```
7. **Sync** → **Run**.

Chọn `pdaChannel=fcm` mà thiếu `google-services.json`: app tự chuyển sang polling và ghi cảnh báo trong Logcat (`CommandChannel`).

---

## 4. Phía backend: service account

Backend cần khóa của Firebase để được phép gửi FCM.

1. Firebase Console → **Project settings** → tab **Service accounts** → **Generate new private key** → tải file JSON.
2. **File này là bí mật**: ai có nó gửi được thông báo tới mọi máy cài app của bạn. Không commit, không gửi qua chat, không dán vào code.
3. Cấu hình **một trong hai** biến môi trường:

| Biến | Giá trị | Dùng khi |
|---|---|---|
| `FIREBASE_SERVICE_ACCOUNT_FILE` | Đường dẫn tới file JSON | Chạy backend trên máy |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | **Toàn bộ nội dung** file JSON | Render (dán vào Environment) |

**Chạy local (PowerShell):**
```powershell
$env:FIREBASE_SERVICE_ACCOUNT_FILE = "C:\keys\andemo-firebase.json"
cd backend
mvn spring-boot:run
```
IntelliJ IDEA: **Run → Edit Configurations → AndemoApplication → Environment variables** → thêm `FIREBASE_SERVICE_ACCOUNT_FILE=C:\keys\andemo-firebase.json`.

**Render:** Dashboard → service → **Environment** → **Add Environment Variable** → key `FIREBASE_SERVICE_ACCOUNT_JSON`, value: mở file JSON bằng Notepad, copy **toàn bộ** nội dung, dán vào → **Save** → deploy lại.

**Kiểm tra:**
- Log backend có dòng `FCM enabled (project <tên-project>)`. Nếu là `FCM disabled: ...` thì đọc lý do ngay trên dòng đó.
- `https://<service>/api/health` trả `{"status":"UP","fcm":true}`.
- Web quản lý hiện "FCM trên server: bật".

Không đặt biến nào thì FCM tắt, polling và WebSocket vẫn chạy bình thường. JSON sai định dạng cũng chỉ làm FCM tắt, backend vẫn khởi động.

**`google-services.json` (app) và service account (backend) phải cùng một Firebase project.** Khác project thì FCM trả `SENDER_ID_MISMATCH`, server tự gỡ token và máy không nhận được lệnh.

---

## 5. Yêu cầu trên thiết bị

| Yêu cầu | Ghi chú |
|---|---|
| **Google Play services** | Máy ảo: system image **"Google APIs"** hoặc **"Google Play"** (Device Manager → cột Target). Image "Android Open Source Project" không có. Máy realme bản quốc tế có sẵn |
| Mạng ra được máy chủ Google | Wi-Fi công ty / cửa hàng có thể chặn (cổng 5228–5230) |
| **Không force-stop app** | Sau force-stop (Settings → Force stop; trên một số máy là vuốt "Xóa tất cả"), FCM không đánh thức app cho tới khi mở lại |
| Quyền thông báo (Android 13+) | App chưa tự xin (bug A3): Settings → Apps → Andemo → Notifications |
| Tắt tối ưu pin (khuyên dùng) | Để app được bật service phát chuông từ background ổn định hơn |

---

## 6. Test

### 6.1 Kiểm tra app đã đăng ký FCM

1. Login trên máy (ví dụ `user`).
2. Logcat, lọc `tag:CommandChannel | tag:FcmTokenRegistrar | tag:MyFirebaseMsgService`:
   ```
   CommandChannel: Command channel: fcm
   FcmTokenRegistrar: FCM token registered with server
   ```
3. Web quản lý: máy có nhãn **FCM** cạnh trạng thái.
4. Thanh thông báo **không** có dòng "Đang chờ lệnh tìm PDA" (chế độ fcm không chạy service nền).

### 6.2 Kịch bản

| Kịch bản | Cách làm | Mong đợi |
|---|---|---|
| Cơ bản | App đang mở → web bấm Tìm | Kêu sau vài giây; log `MyFirebaseMsgService: From: ...`; web 🔔 → bấm Dừng → ✅ |
| **App bị kill** | Mở màn hình đa nhiệm → vuốt Andemo đi (không Force stop) → bấm Tìm | **Vẫn kêu.** Đây là ưu điểm chính của FCM so với polling / WebSocket |
| **Doze** | Khóa màn hình → `adb shell dumpsys deviceidle force-idle` → bấm Tìm | Kêu sau vài giây (message high priority được giao cả khi Doze) |
| Offline trong thời hạn | Bật chế độ máy bay → Tìm (hiệu lực 120 giây) → 1 phút sau tắt chế độ máy bay | Kêu khi có mạng lại |
| Offline quá thời hạn | Hiệu lực 60 giây → chế độ máy bay → Tìm → 2 phút sau tắt | Không kêu (FCM bỏ message theo TTL); web ❌ hết hạn |
| Force-stop | Settings → Apps → Andemo → **Force stop** → Tìm | **Không kêu** tới khi mở lại app. Đây là giới hạn của Android, không phải lỗi |
| Logout | Logout trên app → Tìm | Không kêu; log backend có `FCM token no longer valid, removing it`; web mất nhãn FCM |
| So sánh 3 cách | 3 lần build với `pdaChannel` khác nhau (hoặc 3 máy) → cùng bấm Tìm | So cột "PDA nhận sau" trong bảng Lịch sử |

Sau kịch bản Doze: `adb shell dumpsys deviceidle unforce`.

### 6.3 Mở app là "bắt kịp" lệnh bị lỡ

Ở chế độ fcm, mỗi lần mở app, app hỏi `/api/pda/alerts/pending` 1 lần, phòng khi FCM làm rơi message (máy tắt nguồn quá lâu, bị force-stop…).

---

## 7. Code liên quan

| Phía | File | Vai trò |
|---|---|---|
| App | `command/CommandChannel.java` | Chọn polling / websocket / fcm; fcm thiếu Firebase thì chuyển sang polling |
| App | `fcm/FcmTokenRegistrar.java` | Lấy FCM token, gửi lên server, xóa khi logout |
| App | `fcm/MyFirebaseMessagingService.java` | Nhận message → `AlertDispatcher` → ack `DELIVERED`; `onNewToken` gửi lại token |
| Backend | `push/PushSender.java`, `push/FcmPushSender.java` | Gửi FCM (Firebase Admin SDK), tự tắt khi chưa cấu hình |
| Backend | `service/PdaFinderService.java` (`sendFcm`) | Tạo lệnh xong thì gửi FCM tới các PDA có token |
| Backend | `service/PdaDeviceService.java` | Lưu / gỡ FCM token theo PDA (bảng `pda_device`) |
| Backend | `PUT /api/pda/devices/{deviceId}/fcm-token` | App đăng ký token (`{"fcmToken": "...", "deviceName": "..."}`; token rỗng = hủy) |
| Backend | `GET /api/health` | Có trường `fcm` = server đã cấu hình Firebase chưa |
| Test | `backend/src/test/.../FcmIntegrationTest.java` | 7 test với `PushSender` giả (không cần Firebase thật) |

Nội dung message (data message, không phải notification message, để app tự xử lý kể cả khi ở background):
```json
{ "type": "PDA_FINDER_ALERT", "requestId": "REQ-...", "message": "...", "storeCode": "STORE01", "expiresAt": "2026-..." }
```
Kèm `android.priority = HIGH` và `ttl` = thời gian còn lại của lệnh.

---

## 8. Xử lý lỗi

| Hiện tượng | Nguyên nhân | Cách sửa |
|---|---|---|
| `/api/health` trả `"fcm":false` | Backend chưa có service account | Mục 4; xem dòng `FCM disabled: ...` trong log backend |
| `FCM disabled: cannot load Firebase service account` | Dán thiếu hoặc thừa nội dung JSON, hoặc sai đường dẫn file | Dán lại **toàn bộ** file, kể cả dấu `{` `}` |
| Web không có nhãn FCM | App chưa đăng ký token | Kiểm tra: `pdaChannel=fcm`, có `google-services.json`, đã login, máy có Google Play services; xem log `FcmTokenRegistrar` |
| Log app: `Cannot get FCM token` | Máy ảo không có Google Play services, hoặc không vào được mạng Google | Dùng image "Google APIs"; kiểm tra mạng |
| Log app: `pdaChannel=fcm but app/google-services.json is missing` | Thiếu file | Mục 3 bước 4, rồi Sync + Run |
| Log backend: `FCM send failed ... SENDER_ID_MISMATCH` hoặc token bị gỡ ngay | App và backend dùng 2 Firebase project khác nhau | Tải lại `google-services.json` và service account **cùng một project** |
| Log backend: `Error getting access token for service account` | Service account sai, đã bị xóa, hoặc đồng hồ server sai | Tạo khóa mới (mục 4) |
| Có nhãn FCM nhưng máy không kêu | App bị force-stop; máy không có mạng; thiếu quyền thông báo (Android 13+) | Mở lại app; kiểm tra mạng; mục 5 |
| Kêu nhưng không có popup | Bug A1/A2 (giống 2 cách còn lại) | Tắt chuông bằng nút trên thông báo |
