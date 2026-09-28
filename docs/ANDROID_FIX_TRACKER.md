# Android PDA client: danh sách bug cần fix

Tổng hợp từ bản review Android client. Mỗi khi fix xong một bug thì cập nhật cột **Trạng thái** trong cùng commit.

- **Mức:** P1 = chặn tính năng trên máy thật, P2 = độ tin cậy, P3 = hoàn thiện
- **Buổi:** buổi tương ứng trong plan học
- **Trạng thái:** ✅ Xong · 🟡 Một phần · ⬜ Chưa làm

## Build

| ID | Mức | Bug | File | Trạng thái |
|---|---|---|---|---|
| B1 | P1 | `@PathVariable` (Spring) thay vì `@Path` (Retrofit), lỗi compile | `api/ProductService.java` | ✅ |
| B2 | P1 | Thiếu icon `@mipmap/ic_launcher` | `res/mipmap*` | ✅ |
| B3 | P1 | Plugin google-services bắt buộc có `google-services.json` | `app/build.gradle` | ✅ |
| B4 | P1 | Thiếu Gradle wrapper và `proguard-rules.pro` | `android/` | ✅ |
| B5 | P1 | Conflict manifest merger `screenOrientation` của ZXing `CaptureActivity` | `AndroidManifest.xml` | ✅ |

## PDA Finder Alert

| ID | Mức | Bug | File | Khái niệm cần học | Buổi | Trạng thái |
|---|---|---|---|---|---|---|
| A1 | P1 | Thiếu `USE_FULL_SCREEN_INTENT`; Android 14+ cần check `canUseFullScreenIntent()` | `AndroidManifest.xml`, `PdaAlertService` | Notification, full-screen intent | 5–6 | ⬜ |
| A2 | P1 | `startActivity` từ service bị chặn khi app ở background (Android 10+) | `PdaAlertService.showFullScreenAlert` | Background activity launch | 5–6 | ⬜ |
| A3 | P1 | Không xin `POST_NOTIFICATIONS` lúc runtime → chuông kêu mà không tắt được | `MainActivity` (chưa có) | Runtime permission | 4 | ⬜ |
| A4 | P1 | Alert thứ 2 làm leak MediaPlayer, chuông cũ kêu mãi | `PdaAlertService` | Service lifecycle, `onStartCommand` | 7–8 | ✅ |
| A5 | P1 | `startForegroundService` không try/catch → crash trên Android 12+ | `MyFirebaseMessagingService` | Hạn chế start service từ background | 7–8 | ✅ |
| A6 | P1 | Không lưu và khôi phục volume báo thức gốc | `PdaAlertService` | `AudioManager` | 9 | ✅ |
| A7 | P2 | `setStreamVolume` có thể ném `SecurityException` dưới DND; chưa xử lý DND "Im lặng hoàn toàn" | `PdaAlertService` | DND policy | 9 | 🟡 đã try/catch, chưa xử lý DND |
| A8 | P2 | Không có âm thanh fallback khi máy không có nhạc báo thức | `PdaAlertService` | Resource `res/raw`, `MediaPlayer` | 9 | ⬜ |
| A9 | P2 | Popup không đóng khi timeout hoặc Stop từ notification, màn hình sáng mãi | `AlertActivity` | Giao tiếp Service ↔ Activity | 7–8 | ⬜ |
| A10 | P2 | Không dùng wake lock | `PdaAlertService` | Wake lock, Doze | 11 | ⬜ |
| A11 | P2 | Không bỏ qua alert cũ (`sentTime`), không gửi ack về server | `MyFirebaseMessagingService` | FCM message | 10 | ⬜ |
| A12 | P3 | `START_STICKY` không có tác dụng | `PdaAlertService` | Giá trị trả về `onStartCommand` | 7–8 | ✅ |
| A13 | P3 | Channel chính kêu thêm 1 lần, không `setBypassDnd` | `PdaAlertService` | Notification channel | 5–6 | ⬜ |
| A14 | P3 | `FLAG_DISMISS_KEYGUARD` deprecated; service bỏ qua `requestId` khi Stop | `AlertActivity`, `PdaAlertService` | Lock screen | 5–6 | ⬜ |
| A15 | P3 | Popup có action bar | `AlertActivity`, `themes.xml` | Theme | 3 | ⬜ |
| T1 | P1 | Vòng đời FCM token: `onNewToken` là TODO, không `getToken()` sau login, không retry | `MyFirebaseMessagingService`, `LoginActivity` | FCM token, WorkManager | 10 | 🟡 nhánh `claude/pda-finder-fcm`: đăng ký sau login, gửi lại khi `onNewToken`, xóa khi logout; chưa retry bằng WorkManager |
| T2 | P2 | Không check / hướng dẫn tắt battery optimization | `MainActivity` | Doze, App Standby | 11 | ⬜ |

## Barcode và sản phẩm

| ID | Mức | Bug | File | Khái niệm cần học | Buổi | Trạng thái |
|---|---|---|---|---|---|---|
| S1 (B-1) | P1 | Scanner cứng không tới được: activity `exported=false`, không có intent-filter, extra của broadcast | `QrScanActivity`, `AndroidManifest.xml` | `BroadcastReceiver`, `RECEIVER_EXPORTED` | 12–13 | ⬜ |
| S2 (B-2) | P2 | Không xử lý keyboard wedge; thiếu key `com.symbol.datawedge.data_string` | `QrScanActivity` | Input event, DataWedge | 12–13 | ⬜ |
| S3 (B-3) | P2 | Không validate barcode, không chống scan trùng | `QrScanActivity.onBarcodeScanned` | Intent flags, `launchMode` | 3 | ⬜ |
| S4 (B-4) | P2 | Retrofit callback chạy sau khi activity destroy → Glide crash | `ProductDetailActivity` | Activity lifecycle | 3 | ⬜ |
| S5 (B-5) | P3 | Thiếu barcode thì nút Back/Retry không hoạt động | `ProductDetailActivity` | Activity lifecycle | 3 | ⬜ |
| S6 (B-6) | P3 | Decode ảnh gallery full size trên main thread → OOM/ANR | `QrScanActivity.decodeQrFromUri` | Main thread, bitmap sampling | 3 | ⬜ |
| S7 (B-7) | P3 | Log body (kể cả token) trong release; timeout OkHttp 10s | `api/ApiClient.java` | Build type, OkHttp | 3 | ⬜ |
| S8 | P3 | Popup zoom ảnh chỉ là Toast | `ProductDetailActivity` | Dialog, `ImageView` | 3 | ⬜ |

## Việc cần chốt với khách hàng / BA

| ID | Câu hỏi | Ảnh hưởng |
|---|---|---|
| Q1 | Model PDA cụ thể, có Google Play services (GMS) không? | Không có GMS thì không dùng được FCM, phải làm MQTT/WebSocket/polling |
| Q2 | Mạng cửa hàng có chặn `mtalk.google.com` port 5228–5230 không? | Chặn thì FCM không tới |
| Q3 | Có MDM (StageNow, …) để whitelist battery optimization và cấu hình DataWedge không? | Quyết định T2, S1, S2 làm trong app hay qua MDM |
