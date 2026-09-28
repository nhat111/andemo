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

---

## 4. Android

| Thành phần | File | Vai trò |
|---|---|---|
| `PdaWebSocketService` | `websocket/PdaWebSocketService.java` | Foreground service giữ kết nối, kết nối lại, kiểm tra định kỳ |
| `CommandChannel` | `command/CommandChannel.java` | Chọn WebSocket hay polling lúc build |
| `CommandNotification` | `command/CommandNotification.java` | Notification thường trực dùng chung (`IMPORTANCE_MIN`) |
| Dùng lại từ bản polling | `AlertPoller`, `AlertDispatcher`, `AlertAckReporter`, `BootReceiver` | Poll bắt kịp, chống trùng, ack, khởi động lại sau reboot |

### Giữ kết nối

| Vấn đề | Cách xử lý trong POC |
|---|---|
| NAT / load balancer cắt kết nối nằm im | OkHttp `pingInterval` 30 giây, ngắn hơn timeout mặc định 60 giây của nginx |
| Kết nối chết mà app không biết (khi CPU ngủ, ping không chạy) | Alarm `setAndAllowWhileIdle` mỗi 3 phút: poll pending 1 lần, rồi kết nối lại nếu cần |
| Rớt mạng, server restart | Kết nối lại với backoff 1s, 2s, 4s… tối đa 60s, cộng ngẫu nhiên để hàng loạt PDA không kết nối lại cùng lúc |
| Có mạng trở lại | Kết nối ngay, bỏ qua thời gian chờ backoff |
| Token sai / hết hạn (HTTP 401/403) | Không kết nối lại dồn dập: chờ 60 giây giữa các lần |
| Callback của OkHttp chạy trên thread khác | Mọi thay đổi trạng thái chuyển về 1 `HandlerThread`, nên không cần lock. Callback của kết nối cũ (đã bị thay) thì bỏ qua |

### Chọn kênh lúc build

```bash
./gradlew installDebug -PapiBaseUrl=http://10.0.2.2:8080/                      # WebSocket (mặc định)
./gradlew installDebug -PapiBaseUrl=http://10.0.2.2:8080/ -PpdaChannel=polling # polling
```

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
| Token hết hạn | Đợi token hết hạn (mặc định 24 giờ) | Xem mục 6 |

---

## 6. Hạn chế và phát hiện của POC

- **Token JWT hết hạn sau 24 giờ (`jwt.expiration`), và app không tự làm mới token.** Sau 24 giờ, WebSocket, pending và ack đều bị từ chối, nên **PDA ngừng nhận lệnh cho tới khi user login lại**. Vấn đề này có ở cả bản polling. Cần làm refresh token, hoặc cấp một token riêng dài hạn cho thiết bị (nằm trong phần JWT/backend đang để sau).
- **`JwtFilter` có sẵn ném exception khi token sai hoặc hết hạn**, và log ERROR kèm stack trace ở mỗi request. Client vẫn nhận 403 như mong đợi, nhưng log server sẽ đầy khi nhiều PDA cầm token hết hạn và kết nối lại. Nên bắt `JwtException` trong filter (phần JWT đang để sau).
- **Registry chỉ đúng khi backend chạy 1 instance.** Chạy nhiều instance thì cần Redis pub/sub hoặc message broker để chuyển lệnh tới instance đang giữ kết nối của PDA.
- **H2 in-memory**: restart backend là mất lịch sử lệnh. Production cần database thật.
- **Gói hosting free (ví dụ Render free) không phù hợp**: server ngủ khi rảnh và restart làm rớt mọi kết nối.
- **Alert từ background trên Android 12+**: giống bản polling, cần tắt tối ưu pin để start được `PdaAlertService`; nếu không, app tự chuyển sang notification fallback.
- **Notification thường trực và nút Stop trong "Active apps"**: giống bản polling (xem `PDA_POLLING_DESIGN.md` mục 5.1).
- **Chưa test trên emulator hay máy thật.** Phía backend đã có integration test. Phía client mới kiểm tra bằng đúng phiên bản OkHttp và Gson của app kết nối vào backend thật (handshake qua `http://`, giữ kết nối qua ping, nhận và parse lệnh, bị từ chối khi token sai), chưa chạy trong app Android.
