# PDA Finder Alert - Requirements & Implementation Notes

> Tài liệu này mô tả requirement và cách implement tính năng **PDA Finder Alert** dùng FCM.
> Cập nhật lần cuối: 2026-09-23

---

## 1. Mục tiêu tính năng

Cho phép quản lý / hệ thống gửi lệnh tìm kiếm PDA từ xa.
Khi nhận được lệnh, PDA sẽ:

1. Nhận FCM Data Message (kể cả khi app bị kill)
2. Khởi động **Foreground Service**
3. Hiện notification mức cao nhất + full-screen popup
4. Tăng volume `STREAM_ALARM` lên max và phát âm thanh lặp
5. Cho phép user bấm **Stop** hoặc tự tắt sau timeout

---

## 2. Luồng hoạt động (Sequence)

```
Server  →  FCM (Data Message)  →  MyFirebaseMessagingService
                                      ↓
                              startForegroundService(PdaAlertService)
                                      ↓
                    ┌─────────────────┴─────────────────┐
                    ↓                                   ↓
           High-priority Notification          Full-screen AlertActivity
                    ↓                                   ↓
           Max volume + looping sound          Nút Stop / Timeout
                    ↓                                   ↓
                              stopAlert() → stop service
```

---

## 3. Yêu cầu kỹ thuật chi tiết

### 3.1 Push Notification
- **Bắt buộc dùng Data Message** (không dùng Notification Message).
- Payload mẫu:

```json
{
  "type": "PDA_FINDER_ALERT",
  "requestId": "REQ-20260923-001",
  "storeCode": "STORE01",
  "message": "PDA đang được tìm kiếm bởi quản lý"
}
```

- Priority: `high`
- TTL: 60–120 giây (tùy business)

### 3.2 Foreground Service
- Phải chạy `startForeground()` ngay trong `onStartCommand`.
- Notification category: `CATEGORY_ALARM`
- Priority: `PRIORITY_MAX`
- Có action **Stop**
- Có `fullScreenIntent` để hiện AlertActivity

### 3.3 Âm thanh & Volume
- Dùng `AudioManager.STREAM_ALARM`
- Set volume lên max
- MediaPlayer loop với `USAGE_ALARM`
- Một số PDA doanh nghiệp (Zebra / Urovo) có thể cần SDK riêng để override silent mode

### 3.4 Timeout & Stop
- Timeout mặc định: **60 giây** (có thể config)
- User bấm Stop → dừng ngay + log
- Khi hết timeout → tự dừng

### 3.5 Token Management
- Lưu FCM token lên server khi `onNewToken`
- Cần bảng quản lý device token (userId, deviceId, storeCode, token, updatedAt…)
- Xử lý token hết hạn / revoke

### 3.6 Offline Handling
- Khi PDA offline → FCM sẽ queue message
- Khi online lại sẽ nhận (trong thời gian TTL)
- Cần message rõ ràng khi không thể gửi được

---

## 4. Open Questions (cần customer/BA confirm)

| # | Câu hỏi | Status |
|---|---------|--------|
| 1 | Model PDA cụ thể (Zebra / Urovo version?) | Open |
| 2 | Timeout alert bao lâu? | Open (hiện 60s) |
| 3 | Có cần bypass Do-Not-Disturb / Silent mode mạnh hơn không? | Open |
| 4 | Schema bảng device token chính thức? | Open |
| 5 | Khi app bị force-stop / battery optimization thì behavior mong muốn? | Open |
| 6 | Có cần log lịch sử alert (ai tìm, lúc nào, kết quả)? | Open |

---

## 5. Cấu trúc code đã thêm vào repo

```
android/app/src/main/java/com/example/andemo/
├── fcm/
│   └── MyFirebaseMessagingService.java
├── alert/
│   ├── PdaAlertService.java
│   └── AlertActivity.java
└── res/layout/
    └── activity_alert.xml
```

### Files đã cập nhật
- `AndroidManifest.xml` – thêm permission + service
- `app/build.gradle` – thêm Firebase Messaging
- `build.gradle` (project) – thêm google-services plugin

---

## 6. Cách setup để chạy

1. Tạo project Firebase → thêm Android app (package `com.example.andemo`)
2. Tải `google-services.json` → đặt vào `android/app/`
3. Sync Gradle
4. Chạy app → lấy FCM token từ Logcat (`onNewToken`)
5. Gửi test message từ Firebase Console (phải chọn **Data** message)

---

## 7. Ghi chú quan trọng

- **Không dùng Notification Message** khi app ở background/killed → `onMessageReceived` sẽ không được gọi.
- Trên Android 13+ phải xin quyền `POST_NOTIFICATIONS`.
- PDA doanh nghiệp thường có Battery Optimization rất mạnh → cần guide user tắt tối ưu pin cho app.
- Test trên **máy thật** (emulator không đủ để test volume + foreground service đầy đủ).

---

## 8. Changelog

| Date       | Thay đổi |
|------------|----------|
| 2026-09-23 | Khởi tạo tài liệu + implement FCM cơ bản |
