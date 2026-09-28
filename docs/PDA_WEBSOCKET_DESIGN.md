# PDA Finder: thiết kế và POC phương án WebSocket

> Nhánh: `claude/pda-finder-websocket` (tách từ `claude/pda-finder-polling`). Trạng thái: **POC**, chờ khách hàng chốt phương án.
> So sánh với các phương án khác: [PDA_FINDER_MECHANISM_OPTIONS.md](PDA_FINDER_MECHANISM_OPTIONS.md) (phương án C) · Polling: [PDA_POLLING_DESIGN.md](PDA_POLLING_DESIGN.md)

---

## 1. Đánh giá so với polling

| | Polling | WebSocket (POC này) |
|---|---|---|
| Độ trễ khi máy thức | ≤ 30 giây | **Dưới 1 giây** |
| Độ trễ khi Doze sâu | Vài phút | Dưới 1 giây nếu kết nối còn sống; **vài phút** nếu NAT đã cắt kết nối (chờ alarm kiểm tra phát hiện) |
| Tải server | ~1 request / 30 giây / PDA, phần lớn trả rỗng | 1 kết nối nằm im / PDA, cộng 1 poll bắt kịp mỗi 3 phút |
| Biết PDA đang online | Qua thời điểm poll cuối | Có sẵn: `GET /api/pda/devices/online` |
| Notification thường trực, nút Stop (Android 13+) | Có | **Vẫn có**, rủi ro y như polling |
| Công sức backend | Thấp | Trung bình – cao: registry kết nối; chạy nhiều instance cần Redis pub/sub |

**Kết luận:** WebSocket **nhanh hơn hẳn khi máy đang được dùng**. Khi máy bị bỏ quên, nó chỉ tốt hơn polling nếu mạng cửa hàng giữ được kết nối nằm im, nên **phải đo trên đúng mạng và đúng model PDA**. Nó hợp khi khách muốn độ trễ dưới 1 giây và không muốn thêm hạ tầng mới (đã có backend Spring, không muốn dựng MQTT broker).

---

## 2. Nguyên tắc thiết kế: WebSocket là "chuông cửa", bảng `pda_alert` là nguồn gốc

```
Quản lý (ADMIN) ── POST /api/pda/alerts ──► PdaFinderService
                                               │ 1. lưu vào bảng pda_alert (status SENT)
                                               │ 2. đẩy qua WebSocket tới PDA đang online
                                               ▼
PDA ◄══ WebSocket /ws/pda?deviceId=… ══ PdaSessionRegistry (deviceId → session)
 │
 ├─ nhận lệnh qua WebSocket ──► AlertDispatcher (chống trùng) ──► PdaAlertService (chuông)
 │                                                             └─► POST …/ack DELIVERED
 ├─ mỗi lần kết nối được ──► GET /api/pda/alerts/pending   (bắt kịp lệnh lúc offline)
 └─ alarm mỗi 3 phút ─────► GET /api/pda/alerts/pending + kết nối lại nếu cần
```

- **Server không phải tự lưu và gửi lại lệnh** cho PDA đang offline. Lệnh nằm trong bảng `pda_alert`; PDA kết nối lại thì tự gọi API pending.
- **Cùng một lệnh có thể tới 2 lần** (qua WebSocket và qua poll bắt kịp, hoặc qua cả FCM). `AlertDispatcher` chống trùng theo `requestId`, nên máy chỉ kêu 1 lần.
- Ack vẫn đi qua REST, giống bản polling. WebSocket chỉ dùng cho chiều server → PDA.

---

## 3. Backend (Spring Boot)

| Thành phần | File | Vai trò |
|---|---|---|
| `PdaAlert`, `PdaAlertStatus` | `entity/` | Bảng lệnh tìm; trạng thái chỉ đi tới (`canTransitionTo`) |
| `PdaAlertRepository` | `repository/` | JPA |
| `PdaFinderService` | `service/` | Tạo lệnh (lưu + đẩy WebSocket), pending, ack |
| `PdaAlertController` | `controller/` | REST API bên dưới |
| `PdaWebSocketHandler` | `websocket/` | Nhận kết nối `/ws/pda?deviceId=…` |
| `PdaSessionRegistry` | `websocket/` | Map `deviceId → session`; gửi an toàn đa luồng (`ConcurrentWebSocketSessionDecorator`) |
| `WebSocketConfig` | `config/` | Đăng ký endpoint |

**Xác thực WebSocket:** handshake là một request HTTP thường, nên đi qua `JwtFilter` và `SecurityConfig` có sẵn. Client gửi header `Authorization: Bearer <token>`, không cần sửa cấu hình security.

### API

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| `POST` | `/api/pda/alerts` | ADMIN | Tạo lệnh. Body: `{"deviceId": null \| "…", "message": "…", "storeCode": "…", "ttlSeconds": 120}`. `deviceId` null = mọi PDA |
| `GET` | `/api/pda/alerts` | ADMIN | Lịch sử lệnh và trạng thái |
| `GET` | `/api/pda/devices/online` | ADMIN | PDA đang giữ kết nối WebSocket |
| `GET` | `/api/pda/alerts/pending?deviceId=…` | Đã login | Lệnh `SENT` chưa hết hạn cho PDA này |
| `POST` | `/api/pda/alerts/{requestId}/ack` | Đã login | Body `{"deviceId": "…", "status": "DELIVERED \| STOPPED_BY_USER \| TIMED_OUT"}` → `204`, hoặc `404` nếu không có lệnh đó cho PDA này. Ack lặp lại hoặc lùi trạng thái: bỏ qua, vẫn `204` |
| WS | `/ws/pda?deviceId=…` | Đã login | Server đẩy: `{"type":"PDA_FINDER_ALERT","requestId":"…","message":"…","storeCode":"…"}` |

API pending và ack giống contract của bản polling, nên app bản polling cũng chạy được với backend này.

### Test tự động

`backend/src/test/java/com/example/andemo/PdaFinderIntegrationTest.java` chạy server thật trên cổng ngẫu nhiên và kết nối WebSocket thật. 9 test đều pass:

- Lệnh được đẩy qua WebSocket tới đúng PDA đang online
- Lệnh cho PDA khác thì không bị đẩy nhầm
- Handshake không có token bị từ chối
- PDA offline lấy được lệnh qua pending; ack xong thì lệnh biến khỏi pending
- Trạng thái không bị lùi (`DELIVERED` đến sau `STOPPED_BY_USER` bị bỏ qua)
- Ack lệnh không tồn tại, hoặc lệnh của PDA khác, trả `404`
- Lệnh hết hạn không còn trong pending
- Lệnh gửi cho mọi PDA thì PDA nào cũng thấy
- Chỉ ADMIN được tạo lệnh

```bash
cd backend && mvn test
```

### Xác thực: access token + refresh token

Trước đây access token sống 24 giờ và không làm mới được, nên sau 24 giờ PDA ngừng nhận lệnh cho tới khi có người login lại. Nay:

| Token | Thời hạn | Ghi chú |
|---|---|---|
| Access token (JWT) | 1 giờ (`jwt.expiration`) | Gửi trong header `Authorization: Bearer …` |
| Refresh token | 30 ngày (`jwt.refresh-expiration-days`) | Chuỗi ngẫu nhiên; server chỉ lưu hash SHA-256 |

- `POST /api/auth/login` trả thêm `refreshToken`.
- `POST /api/auth/refresh` với body `{"refreshToken": "…"}` trả **cặp token mới**; refresh token cũ bị thu hồi (xoay vòng). PDA còn liên lạc với server thì phiên được gia hạn mãi; im lặng quá 30 ngày thì phải login lại.
- **Refresh token đã thu hồi mà bị dùng lại** (dấu hiệu bị lộ): server thu hồi **toàn bộ** refresh token của user đó, buộc login lại.
- `POST /api/auth/logout` với body `{"refreshToken": "…"}` thu hồi refresh token.
- Token sai, hết hạn, ký bằng khóa khác, hoặc thiếu token: **401** (trước đây là 403, kèm log ERROR có stack trace ở mỗi request). Không có quyền (ví dụ USER gọi API của ADMIN): **403**. Client dựa vào mã 401 để biết khi nào cần làm mới token.

---

### Requester: web quản lý và nút "Tìm PDA"

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| `GET` | `/api/pda/devices` | ADMIN | PDA server biết: tên máy, user đang login, `lastSeenAt`, `secondsSinceLastSeen`, `online` (đang giữ WebSocket hoặc poll trong 90 giây), `connected` (đang giữ WebSocket) |
| `GET` | `/api/pda/alerts/{requestId}` | ADMIN | Trạng thái 1 lệnh, có cờ `expired` |
| `GET` | `/pda-finder.html` (hoặc `/`) | Công khai | Web quản lý; dữ liệu trên trang vẫn cần login ADMIN |
| `GET` | `/api/health` | Công khai | Health check cho Render |

Bảng `pda_device` được cập nhật mỗi lần PDA liên lạc: poll pending (`deviceName` là query param), kết nối WebSocket (`/ws/pda?deviceId=…&deviceName=…`), và ngắt WebSocket. Deploy: xem [DEPLOY_RENDER.md](DEPLOY_RENDER.md).

## 4. Android

| Thành phần | File | Vai trò |
|---|---|---|
| `PdaWebSocketService` | `websocket/PdaWebSocketService.java` | Foreground service giữ kết nối, kết nối lại, kiểm tra định kỳ |
| `CommandChannel` | `command/CommandChannel.java` | Chọn WebSocket hay polling lúc build |
| `CommandNotification` | `command/CommandNotification.java` | Notification thường trực dùng chung (`IMPORTANCE_MIN`) |
| Dùng lại từ bản polling | `AlertPoller`, `AlertDispatcher`, `AlertAckReporter`, `BootReceiver` | Poll bắt kịp, chống trùng, ack, khởi động lại sau reboot |
| `TokenRefresher` | `api/TokenRefresher.java` | Đổi refresh token lấy cặp token mới. Chỉ 1 thread làm mới tại một thời điểm, các thread khác chờ rồi dùng token mới (quan trọng vì server xoay vòng token) |
| `TokenAuthenticator` | `api/TokenAuthenticator.java` | OkHttp gọi khi REST trả 401: làm mới token rồi gửi lại đúng request đó. Poll, ack, màn hình… không cần biết token đã hết hạn |

### Giữ kết nối

| Vấn đề | Cách xử lý trong POC |
|---|---|
| NAT / load balancer cắt kết nối nằm im | OkHttp `pingInterval` 30 giây, ngắn hơn timeout mặc định 60 giây của nginx |
| Kết nối chết mà app không biết (khi CPU ngủ, ping không chạy) | Alarm `setAndAllowWhileIdle` mỗi 3 phút: poll pending 1 lần, rồi kết nối lại nếu cần |
| Rớt mạng, server restart | Kết nối lại với backoff 1s, 2s, 4s… tối đa 60s, cộng ngẫu nhiên để hàng loạt PDA không kết nối lại cùng lúc |
| Có mạng trở lại | Kết nối ngay, bỏ qua thời gian chờ backoff |
| Access token hết hạn (handshake 401) | Làm mới token bằng `TokenRefresher` rồi kết nối lại ngay. Refresh token cũng hết hạn thì logout và dừng service |
| Không có quyền (403), hoặc làm mới token thất bại vì mạng/server | Không kết nối lại dồn dập: chờ 60 giây giữa các lần |
| Callback của OkHttp chạy trên thread khác | Mọi thay đổi trạng thái chuyển về 1 `HandlerThread`, nên không cần lock. Callback của kết nối cũ (đã bị thay) thì bỏ qua |

### Chọn kênh lúc build

```bash
./gradlew installDebug -PapiBaseUrl=http://10.0.2.2:8080/                      # WebSocket (mặc định)
./gradlew installDebug -PapiBaseUrl=http://10.0.2.2:8080/ -PpdaChannel=polling # polling
```

Nhánh `claude/pda-finder-fcm` thay `pdaChannel` bằng 3 app riêng (product flavors): `installPollingDebug`, `installWebsocketDebug`, `installFcmDebug`. Xem `FCM_SETUP.md` mục 1.

FCM, nếu có `google-services.json`, luôn chạy song song với kênh được chọn.

---

## 5. Chạy thử end-to-end

```bash
# 1. Chạy backend (H2 in-memory, có sẵn user: admin / user / user2, mật khẩu 123456)
cd backend && mvn spring-boot:run

# 2. Cài app trỏ vào backend local
cd android
./gradlew installDebug -PapiBaseUrl=http://10.0.2.2:8080/        # emulator
./gradlew installDebug -PapiBaseUrl=http://192.168.1.10:8080/    # máy thật: IP LAN của máy tính

# 3. Trên PDA: login bằng user / 123456. Log backend sẽ có: "PDA connected: <deviceId>"

# 4. Lấy token admin, tạo lệnh tìm
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')

curl -X POST localhost:8080/api/pda/alerts -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"message":"Tìm máy kho A"}'
#    → PDA kêu gần như ngay lập tức

# 5. Xem PDA đang online và trạng thái các lệnh
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/pda/devices/online
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/pda/alerts
```

**Các kịch bản nên test:**

| Kịch bản | Cách làm | Kết quả mong đợi |
|---|---|---|
| Nhận lệnh tức thì | Tạo lệnh khi app đang mở | Kêu trong ~1 giây; trạng thái `DELIVERED` |
| Server restart | Tắt backend 30 giây rồi bật lại | App tự kết nối lại (xem log `PdaWebSocketService`) |
| Lệnh gửi lúc mất mạng | Tắt wifi, tạo lệnh, bật wifi trong 2 phút | Kết nối lại, poll bắt kịp, kêu |
| Không kêu 2 lần | Tạo lệnh khi đang online | Chỉ kêu 1 lần dù lệnh tới qua cả WebSocket và poll bắt kịp |
| Máy vào Doze | Tắt màn hình, `adb shell dumpsys deviceidle force-idle`, đợi vài phút, tạo lệnh | **Đo độ trễ thực tế.** Đây là số liệu quan trọng nhất để so với polling |
| PDA online/offline | Mở app, rồi logout | `/api/pda/devices/online` có rồi mất `deviceId` |
| Token hết hạn | Chạy backend với `--jwt.expiration=60000` (1 phút), đợi 2 phút rồi tạo lệnh | App tự làm mới token (logcat `TokenRefresher: Access token refreshed`) và vẫn nhận lệnh |
| Phiên hết hạn | Chạy backend với `--jwt.expiration=60000`, login trên PDA, **restart backend** (H2 in-memory mất hết refresh token), đợi 2 phút | Lần làm mới bị 401 → app logout, notification thường trực biến mất |

---

## 6. Hạn chế và phát hiện của POC

- ~~Token hết hạn sau 24 giờ thì PDA ngừng nhận lệnh~~ và ~~`JwtFilter` log ERROR khi token sai~~: **đã sửa**, xem "Xác thực" ở mục 3.
- **PDA không liên lạc với server quá 30 ngày** (ví dụ cất trong kho) thì refresh token hết hạn và phải login lại. Có thể tăng `jwt.refresh-expiration-days` nếu khách cần.
- **App bị logout khi đang mở màn hình chính** (refresh token bị thu hồi): service tự dừng, nhưng màn hình chưa tự chuyển về trang login; thao tác tiếp theo mới báo lỗi.
- **Access token đã cấp vẫn dùng được tới khi hết hạn** (tối đa 1 giờ) kể cả sau logout. Đây là đặc điểm chung của JWT không trạng thái.
- **Registry chỉ đúng khi backend chạy 1 instance.** Chạy nhiều instance thì cần Redis pub/sub hoặc message broker để chuyển lệnh tới instance đang giữ kết nối của PDA.
- **H2 in-memory**: restart backend là mất lịch sử lệnh **và toàn bộ refresh token**, nên mọi PDA bị logout khi access token hết hạn (tối đa 1 giờ sau restart). Production bắt buộc dùng database thật.
- **Gói hosting free (ví dụ Render free) không phù hợp**: server ngủ khi rảnh và restart làm rớt mọi kết nối.
- **Alert từ background trên Android 12+**: giống bản polling, cần tắt tối ưu pin để start được `PdaAlertService`; nếu không, app tự chuyển sang notification fallback.
- **Notification thường trực và nút Stop trong "Active apps"**: giống bản polling (xem `PDA_POLLING_DESIGN.md` mục 5.1).
- **Chưa test trên emulator hay máy thật.** Phía backend đã có integration test. Phía client mới kiểm tra bằng đúng phiên bản OkHttp và Gson của app kết nối vào backend thật (handshake qua `http://`, giữ kết nối qua ping, nhận và parse lệnh, bị từ chối khi token sai), chưa chạy trong app Android.
