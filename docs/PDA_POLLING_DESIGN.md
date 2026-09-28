# PDA Finder: thiết kế và POC phương án Polling

> Nhánh: `claude/pda-finder-polling`. Trạng thái: **POC**, chờ khách hàng chốt phương án.
> So sánh với các phương án khác: [PDA_FINDER_MECHANISM_OPTIONS.md](PDA_FINDER_MECHANISM_OPTIONS.md) (phương án B).

---

## 1. Đánh giá: polling phù hợp khi nào

**Polling phù hợp khi:**
- PDA **không có Google Play services**, hoặc mạng cửa hàng chặn Google, nên không dùng được FCM.
- Khách chấp nhận **độ trễ vài chục giây khi đang dùng máy và vài phút khi máy bị bỏ quên**.
- Khách chấp nhận **notification thường trực** "Đang chờ lệnh tìm PDA".
- App được **whitelist khỏi battery optimization**. Nên làm qua MDM.
- Muốn backend đơn giản nhất: chỉ REST thường, không phải giữ kết nối, không thêm broker.

**Polling không phù hợp khi:**
- Cần độ trễ vài giây kể cả lúc máy đang Doze. Khi đó dùng FCM, hoặc MQTT nếu không có GMS.
- Số lượng PDA lớn mà hạ tầng server hạn chế. 1.000 máy poll mỗi 30 giây là khoảng 33 request/giây liên tục.

**Kết luận:** polling là phương án **dễ làm và dễ vận hành nhất**, nhưng **độ trễ tệ nhất đúng lúc cần nhất** (máy bị bỏ quên). Nó hợp làm kênh chính cho đội máy không có GMS nếu khách chấp nhận độ trễ vài phút. Nó cũng hợp làm lớp "bắt kịp" bổ sung cho FCM.

---

## 2. Kiến trúc

```
MainActivity.onResume ─┐
BootReceiver ──────────┼─► PdaPollingService (foreground service, notification thường trực)
Alarm (Doze) ──────────┤        │  mỗi 30s + khi có mạng lại
Có mạng lại ───────────┘        ▼
                          AlertPoller.pollOnce()
                                │  GET /api/pda/alerts/pending?deviceId=…
                                ▼
FCM (nếu có) ──────────► AlertDispatcher.dispatch()   ← chống trùng theo requestId (lưu bền)
                                │
                                ├─► PdaAlertService (chuông, volume, popup, timeout)
                                │        └─► ack STOPPED_BY_USER / TIMED_OUT
                                └─► ack DELIVERED
```

| Thành phần | File | Vai trò |
|---|---|---|
| `PdaPollingService` | `polling/PdaPollingService.java` | Foreground service `specialUse`, lập lịch poll, poll ngay khi có mạng lại |
| `AlertPoller` | `polling/AlertPoller.java` | Gọi API pending 1 lần, chuyển từng lệnh cho dispatcher, gửi ack `DELIVERED` |
| `BootReceiver` | `polling/BootReceiver.java` | Khởi động lại polling sau khi reboot hoặc cập nhật app |
| `AlertDispatcher` | `alert/AlertDispatcher.java` | Điểm vào chung cho FCM và polling; chống trùng; start `PdaAlertService`, fallback sang notification |
| `ProcessedAlertStore` | `alert/ProcessedAlertStore.java` | Lưu `requestId` đã xử lý trong 24 giờ (SharedPreferences) |
| `AlertAckReporter` | `alert/AlertAckReporter.java` | Gửi trạng thái lệnh về server |
| `AlertApi` | `api/AlertApi.java` | Retrofit interface cho các endpoint ở mục 3 |
| `FindPdaActivity` | `requester/FindPdaActivity.java` | Màn hình requester (chỉ ADMIN): danh sách PDA, gửi lệnh, theo dõi trạng thái |

**Vòng đời:**
- Service start ở `MainActivity.onResume` (khi đã login) và ở `BootReceiver`.
- Mỗi lần start đều poll ngay, nên mở app cũng là một lần "bắt kịp".
- Logout: dừng service. Mỗi lần poll cũng kiểm tra login, nếu đã logout thì tự dừng.
- Bị hệ thống kill: `START_STICKY` xin khởi động lại.

### 2.1 Luồng đầy đủ: từ nút "Tìm PDA" tới khi tìm thấy máy

Cùng một app, 2 vai trò: **requester** (quản lý, login `admin`) và **PDA** (nhân viên, login user khác). Máy nào đã login đều chạy polling, nên máy của quản lý cũng là một PDA tìm được.

```
 Requester (ADMIN)                     Server                               PDA
 ─────────────────                     ──────                               ───
                                                         ◄── (0) GET /pending?deviceId&deviceName   mỗi ~30s
                                                              server ghi nhận: máy này vừa liên lạc
 (1) Bấm "Tìm PDA"
     GET /api/pda/devices ───────────►  danh sách PDA + online/offline
 (2) Chọn máy, bấm "Tìm"
     POST /api/pda/alerts ───────────►  lưu lệnh, status = SENT
                                        (không đẩy được xuống PDA:
                                         polling nên phải chờ PDA hỏi)
 (3) mỗi 3s: GET /alerts/{id} ───────►  "SENT"   ⏳ Đang chờ PDA nhận
                                                         ◄── (4) GET /pending → trả lệnh
                                                              AlertDispatcher → PdaAlertService
                                                              🔔 đổ chuông, max volume, popup
                                        status = DELIVERED ◄── (5) POST /ack DELIVERED
 (6) GET /alerts/{id} ───────────────►  "DELIVERED" 🔔 PDA đang đổ chuông
                                                              người tìm thấy máy, bấm "Dừng Alert"
                                        status = STOPPED_BY_USER ◄── (7) POST /ack STOPPED_BY_USER
 (8) GET /alerts/{id} ───────────────►  "STOPPED_BY_USER" ✅ đã tìm thấy
```

**Thời gian chờ ở bước (4)** chính là điểm yếu của polling: bằng khoảng thời gian tới lần poll kế tiếp của PDA (≤ 30 giây khi máy thức, vài phút khi Doze). Với WebSocket, server đẩy lệnh xuống ngay ở bước (2).

Các kết thúc khác ở bước (8):
- `TIMED_OUT`: chuông kêu hết 60 giây mà không ai tắt.
- `SENT` + `expired`: hết hạn lệnh (2 phút) mà PDA không hỏi server lần nào: máy tắt nguồn, mất mạng, hoặc app bị dừng.

---

## 3. API contract (backend mock)

### `GET /api/pda/alerts/pending?deviceId={deviceId}`

Trả các lệnh cho thiết bị này (hoặc cho mọi thiết bị) **đang ở trạng thái `SENT` và chưa hết hạn**. Server lọc hết hạn theo giờ của server, vì giờ trên PDA có thể sai.

```json
[
  { "requestId": "REQ-20260928-001", "message": "PDA đang được tìm kiếm bởi quản lý", "storeCode": "STORE01" }
]
```

### `POST /api/pda/alerts/{requestId}/ack`

```json
{ "deviceId": "a1b2c3…", "status": "DELIVERED" }
```

- `status`: `DELIVERED` | `STOPPED_BY_USER` | `TIMED_OUT`
- Response: `204`, hoặc `404` nếu không có lệnh đó.
- **Server không cho trạng thái lùi.** App có thể gửi lại `DELIVERED` sau `STOPPED_BY_USER` (khi lần ack trước thất bại), server phải bỏ qua.
- Sau khi nhận `DELIVERED`, lệnh không còn nằm trong danh sách pending.

### Phía requester (chỉ ADMIN, USER nhận `403`)

| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/api/pda/devices` | PDA server biết: `[{"deviceId","deviceName","secondsSinceLastSeen","online"}]`, máy liên lạc gần nhất đứng đầu. `online` = poll trong 90 giây gần nhất. Server tự tính theo giờ của server |
| `POST` | `/api/pda/alerts` | Body `{"deviceId": "…" \| null, "message": "…", "ttlSeconds": 120}` → `201` + trạng thái lệnh. `deviceId` null = mọi PDA |
| `GET` | `/api/pda/alerts/{requestId}` | Trạng thái 1 lệnh: `{"requestId","deviceId","message","status","expired"}` |

`GET /pending` nhận thêm `deviceName` (ví dụ `realme RMX1851`). Server dùng các lần poll để biết PDA nào tồn tại và lần cuối liên lạc lúc nào.

Mọi endpoint dùng header `Authorization: Bearer <token>` như các API khác.

---

## 4. Lịch poll và hành vi khi Doze

App dùng **3 nguồn kích hoạt poll** song song, nguồn nào đến trước thì poll:

| Nguồn | Khi máy thức | Khi máy Doze |
|---|---|---|
| `Handler.postDelayed(30s)` | Đúng 30 giây | **Gần như dừng hẳn.** Handler tính theo thời gian CPU chạy, CPU ngủ thì không đếm |
| `AlarmManager.setAndAllowWhileIdle(30s)` | Khoảng 30 giây (không chính xác tuyệt đối) | Đánh thức được máy, nhưng **hệ thống giới hạn tần suất**, thường vài phút một lần tùy phiên bản Android |
| `NetworkCallback.onAvailable` | Ngay khi có mạng lại | Ngay khi có mạng lại |

Mỗi lần poll giữ partial wake lock tối đa 20 giây, để CPU không ngủ giữa chừng lúc đang gọi HTTP.

**Không dùng alarm chính xác** (`setExactAndAllowWhileIdle`): từ Android 12 cần quyền `SCHEDULE_EXACT_ALARM`, và Android 14 mặc định không cấp quyền này cho app không phải báo thức/lịch. Kể cả có quyền, khi Doze hệ thống vẫn giới hạn tần suất.

**Độ trễ dự kiến** (cần đo trên đúng model PDA):

| Trạng thái máy | Độ trễ |
|---|---|
| Đang dùng, màn hình bật | ≤ 30 giây |
| Màn hình tắt, chưa vào Doze | ~30 giây đến vài phút |
| Bị bỏ quên, Doze sâu | Vài phút (theo giới hạn alarm của hệ thống) |
| Mất mạng rồi có lại | Poll ngay khi có mạng |

---

## 5. Điều kiện để chạy ổn định trên PDA

1. **Tắt battery optimization cho app.** Không tắt thì:
   - Hãng máy có thể kill service.
   - Trên Android 12+, khi app ở background, poll nhận được lệnh nhưng **không start được `PdaAlertService`**. App tự chuyển sang notification fallback (có chuông lặp, nhưng không tăng volume).
2. **Cấp quyền notification** (Android 13+, bug A3 trong tracker). Thiếu quyền thì notification thường trực, popup alert và fallback đều không hiện.
3. Người dùng **không force-stop app**. Sau force-stop, không có gì chạy lại cho tới khi user mở app.
4. Người dùng **không bấm Stop app trong "Active apps"** (Android 13+). Xem mục 5.1.

### 5.1 Notification thường trực và nút Stop trong "Active apps"

Polling chạy trong foreground service, nên **bắt buộc có notification**. Từ Android 8, mọi foreground service phải gắn với một notification, app không tự ẩn được. Chi tiết theo từng phiên bản Android: xem [mục 4.5 tài liệu phân tích](PDA_FINDER_MECHANISM_OPTIONS.md#45-notification-thường-trực-phương-án-b2-c-d).

POC dùng kênh **`IMPORTANCE_MIN`**:
- Không kêu.
- Không có icon trên thanh trạng thái.
- Chỉ nằm thu gọn ở cuối danh sách khi kéo thanh thông báo xuống.

Đây là mức ít gây chú ý nhất mà Android cho phép với foreground service.

**Rủi ro chính:** từ Android 13, app có foreground service xuất hiện trong mục **"Active apps"** ở cuối thanh thông báo, kèm nút **Stop**. Nhân viên bấm Stop thì cả app dừng, polling ngừng, và PDA không nhận lệnh tìm nữa cho tới khi có người mở lại app. `IMPORTANCE_MIN` **không** bỏ được nút này.

**Giảm thiểu:**
- MDM cài app ở chế độ **device owner**: Android không hiện nút Stop cho app đó.
- Server theo dõi thời điểm poll cuối của từng PDA (`last_seen_at`) và cảnh báo khi một máy lâu không poll.
- Hướng dẫn nhân viên không dừng app (biện pháp yếu nhất).

Khách cần trả lời câu 5a/5b trong tài liệu phân tích.

---

## 6. Test với mock server

Mock server viết bằng Java thuần, không cần thư viện: `tools/MockPdaAlertServer.java`. Nó có thêm endpoint login và items giả để app đăng nhập được.

```bash
# 1. Chạy mock server từ thư mục gốc repo (JDK 17+)
java tools/MockPdaAlertServer.java            # cổng 8081
#    Web quản lý: mở http://localhost:8081/ trên máy tính, login "admin" (mật khẩu bất kỳ)

# 2. Build app trỏ vào mock server
cd android
./gradlew installDebug -PapiBaseUrl=http://10.0.2.2:8081/          # emulator
./gradlew installDebug -PapiBaseUrl=http://192.168.1.10:8081/      # máy thật: IP LAN của máy tính

# 3. Máy PDA (ví dụ realme): login bằng "user" (mật khẩu bất kỳ).
#    Thanh thông báo có "Đang chờ lệnh tìm PDA"; log mock server thấy GET /pending mỗi ~30 giây.

# 4. Requester, chọn 1 trong 2:
#    a) Web quản lý http://localhost:8081/ → bảng "Chọn PDA cần tìm" → bấm "🔔 Tìm" ở dòng realme
#    b) App trên máy khác (ví dụ emulator): login "admin" → nút "Tìm PDA" → chọn máy → Tìm
#    Trạng thái: ⏳ chờ PDA nhận → 🔔 đang đổ chuông → (bấm "Dừng Alert" trên realme) → ✅ đã tìm thấy

# Hoặc tạo lệnh bằng curl (không gửi token thì mock coi như ADMIN):
curl -X POST "http://localhost:8081/api/pda/alerts?message=Tim%20may%20kho%20A"
curl http://localhost:8081/api/pda/devices
curl http://localhost:8081/api/pda/alerts
```

### Web quản lý

File `backend/src/main/resources/static/pda-finder.html`: một file HTML, không dùng thư viện ngoài. Mock server phục vụ tại `/`; backend Spring (nhánh websocket) phục vụ tại `/pda-finder.html`. Trang gồm:

1. **Chọn PDA cần tìm:** danh sách PDA (tên máy, deviceId, user đang login, online/offline, lần liên lạc cuối), kèm các ô nội dung, mã cửa hàng, hiệu lực lệnh 60/90/120 giây (requirement §3.1). PDA offline thì hỏi xác nhận trước khi gửi (§3.6).
2. **Theo dõi lệnh:** các bước Đã gửi → PDA đã nhận, đang đổ chuông → Đã tìm thấy, kèm thời gian PDA nhận lệnh.
3. **Lịch sử tìm kiếm:** ai tìm, lúc nào, máy nào, kết quả (Open Question #6).
4. **Đối chiếu requirement:** bảng cho biết requirement nào đã có, còn thiếu gì.

**Chỉ có 1 máy?** Dùng web quản lý trên máy tính làm requester, máy realme làm PDA. Hoặc login bằng `admin` trên chính máy đó. Máy vừa là requester vừa là PDA: trong danh sách chọn dòng "(máy này)", bấm Tìm, rồi chờ ≤ 30 giây là máy tự kêu. Popup báo động sẽ đè lên màn hình requester; bấm "Dừng Alert" rồi quay lại sẽ thấy ✅.

Máy thật và máy tính phải cùng mạng wifi, và firewall của máy tính phải mở cổng 8081.

**Các kịch bản nên test:**

| Kịch bản | Cách làm | Kết quả mong đợi |
|---|---|---|
| Requester tìm thấy máy | Admin bấm "Tìm PDA" → chọn máy → Tìm; trên PDA bấm "Dừng Alert" | Màn hình requester: ⏳ → 🔔 → ✅ |
| Requester: chuông tự tắt | Như trên nhưng không bấm Dừng trên PDA | Sau ~60 giây: ⏱ hết thời gian |
| Requester: PDA offline | Tắt wifi trên PDA, đợi 2 phút để thành Offline, gửi lệnh, đợi thêm 2 phút | Danh sách hiện ⚪ Offline, hỏi xác nhận trước khi gửi; cuối cùng ❌ hết hạn |
| Nút chỉ dành cho ADMIN | Login bằng `user` | Không có nút "Tìm PDA"; gọi API bằng token user bị `403` |
| Nhận lệnh khi đang mở app | Tạo lệnh | Kêu trong ≤ 30s, ack `DELIVERED` |
| 2 lệnh liên tiếp | Tạo 2 lệnh cách nhau 10 giây | Chỉ 1 tiếng chuông (lệnh sau thay lệnh trước), cả 2 được ack `DELIVERED` |
| Lệnh gửi lúc mất mạng | Tắt wifi, tạo lệnh, bật wifi trong vòng 2 phút | Kêu ngay khi có mạng |
| Lệnh hết hạn | Tắt wifi, tạo lệnh với `ttl=30`, đợi 1 phút, bật wifi | Không kêu |
| Máy vào Doze | Tắt màn hình, `adb shell dumpsys deviceidle force-idle`, tạo lệnh | Đo độ trễ thực tế |
| Khởi động lại máy | Reboot, không mở app, tạo lệnh | Vẫn kêu (nhờ `BootReceiver`) |
| Logout | Logout | Notification thường trực biến mất, mock server hết thấy request |
| Notification ít gây chú ý | Kéo thanh thông báo xuống | Không có icon trên thanh trạng thái; notification "Đang chờ lệnh tìm PDA" nằm thu gọn ở cuối |
| Stop trong "Active apps" (Android 13+) | Kéo thanh thông báo, bấm "Active apps" → Stop cạnh Andemo | Mock server hết thấy request poll; tạo lệnh thì PDA không kêu cho tới khi mở lại app |
| Chưa tắt tối ưu pin (Android 12+) | Để app ở background, tạo lệnh | Kiểm tra logcat `AlertDispatcher`: nếu thấy `showing fallback notification` thì service bị chặn và app đã chuyển sang notification fallback |

---

## 7. Hạn chế của bản POC

- **Ack `STOPPED_BY_USER` / `TIMED_OUT` gửi 1 lần, không retry.** Mất mạng lúc đó thì server không biết. Production nên đưa vào WorkManager.
- **`deviceId` dùng `ANDROID_ID`**, sẽ đổi khi factory reset. Production nên dùng mã tài sản từ MDM.
- **Chưa có màn hình hướng dẫn tắt tối ưu pin và xin quyền notification** (T2, A3 trong tracker).
- **Chu kỳ poll cố định 30 giây.** Có thể cho server trả về chu kỳ, hoặc giãn chu kỳ khi máy không di chuyển để tiết kiệm pin.
- **Chưa đo pin thực tế.** Cần chạy thử một ca làm việc (8–10 giờ) trên đúng model PDA, so sánh mức pin với khi tắt polling.
