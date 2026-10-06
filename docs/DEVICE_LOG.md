# Log trên thiết bị (PDA)

Yêu cầu của khách: khi phát triển Android, log trên thiết bị là hạng mục quan trọng.
Gửi log lên server và lưu vào table **chưa nằm trong scope** ("필요한 경우는 요건에 추가 예정").

## Vì sao không đủ Logcat

Logcat là bộ nhớ vòng của hệ thống, vài phút tới vài giờ là bị ghi đè, và PDA ngoài hiện trường
không cắm USB để `adb logcat` được. Khi có sự cố ("máy X không kêu lúc 10 giờ") cần log còn lưu trên máy.

## Cách hoạt động

- Code gọi `DeviceLog.d/i/w/e(TAG, …)` thay cho `android.util.Log`: vẫn ra Logcat như cũ, đồng thời
  ghi vào file (`android/app/src/main/java/com/example/andemo/log/DeviceLog.java`).
- File: `filesDir/logs/device.log`, xoay vòng `device.1.log` … `device.4.log`, mỗi file 1 MB,
  **tổng tối đa ~5 MB** (file cũ nhất bị xóa). Nằm trong bộ nhớ riêng của app: gỡ app là mất.
- Ghi trên 1 thread riêng, không chặn main thread.
- Mỗi lần app khởi động có 1 dòng header:
  ```
  2026-10-06 10:15:00.123 +0900 I/DeviceLog [main] === App start: com.example.andemo.websocket 1.0 (1), channel=websocket, debug=false, deviceId=…, device=Zebra TC21, Android 11 (API 30)
  ```
  Nhờ đó biết máy đang chạy **version nào** lúc xảy ra sự cố (nhiều version cùng tồn tại, rollback).
- Crash (uncaught exception) được ghi kèm stack trace trước khi app chết.
- Định dạng dòng: `thời gian (có múi giờ) mức/TAG [thread] nội dung`.

## Những gì đang được ghi

| Nhóm | Ví dụ |
|---|---|
| Kênh nhận lệnh | WebSocket connect / disconnect / reconnect, poll ok / lỗi, FCM message, đổi kênh |
| Lệnh tìm PDA | nhận lệnh, trùng lệnh, bắt đầu / dừng alert, fallback notification, ack gửi / lỗi |
| Phiên đăng nhập | login ok / bị từ chối / lỗi mạng, logout, làm mới token, bị logout vì refresh token hết hạn |
| Khác | barcode quét được, khởi động lại sau reboot / update app, crash |

**Không ghi** mật khẩu, access/refresh token, FCM token. Dòng nào cần in dữ liệu nhạy cảm để debug thì
dùng `android.util.Log` trực tiếp (chỉ ra Logcat), như dòng in FCM token trong `FcmTokenRegistrar`.

## Lấy log ra

Nút **"Xuất log"** ở màn hình Login và màn hình chính (`LogExporter`): gộp các file (cũ → mới) thành
`andemo-log-<deviceId>-<yyyyMMdd-HHmmss>.txt` rồi mở màn hình chia sẻ của Android (email, Drive,
Bluetooth…). Có ở màn Login vì sự cố hay gặp là PDA bị logout.

Khi debug bằng cáp: `adb shell run-as com.example.andemo.websocket cat files/logs/device.log`
(chỉ bản debug).

## Khi khách thêm requirement gửi log lên server

Đã có sẵn file và định dạng; phần cần thêm:
- API nhận log (ví dụ `POST /api/pda/logs`, gzip) + table lưu, có `deviceId`, `appVersion`, khoảng thời gian.
- Upload bằng WorkManager (chỉ khi có mạng, retry), hoặc server ra lệnh "gửi log" qua kênh nhận lệnh hiện có.
- Giới hạn dung lượng / tần suất để 1.000 máy không cùng upload một lúc.
