# Plan học Android dựa trên project Andemo

> Dành cho: dev Java/Spring, lâu rồi chưa code Android.
> Thời lượng: ~3 tuần, mỗi ngày 1–2 tiếng (14 buổi). Làm nhanh hơn hay chậm hơn đều được, miễn làm đúng thứ tự.
> Cập nhật: 2026-09-28

---

## Cách học

1. **Đọc khái niệm** của buổi (tài liệu gợi ý ở cuối file).
2. **Đọc code thật** trong project ở cột "Đọc code". Code có comment tiếng Việt giải thích *vì sao* viết như vậy.
3. **Làm bài tập**: phần lớn là bug thật trong [ANDROID_FIX_TRACKER.md](ANDROID_FIX_TRACKER.md).
4. **Tự trả lời câu hỏi kiểm tra.** Trả lời được mà không nhìn tài liệu thì qua buổi sau.
5. Push lên một nhánh riêng, nhờ Claude review diff và giải thích chỗ sai.

### Các nhánh

| Nhánh | Có gì | Dùng cho buổi |
|---|---|---|
| `claude/android-pda-client-review-ryoac9` | Code gốc đã build được, fix A4–A6, tracker, tài liệu phân tích | 1–6, 9, 12–13 |
| `claude/pda-finder-polling` | + Polling, nút "Tìm PDA", web quản lý, mock server | 3, 7–8, 10, 14 |
| `claude/pda-finder-websocket` | + WebSocket, backend Spring, refresh token | 11 |

Nhánh sau chứa toàn bộ nhánh trước, nên học trên nhánh websocket cũng được, chỉ là nhiều code hơn.

### Công cụ

- Android Studio bản mới, JDK 17+, Android SDK 34.
- Máy realme (Android 11) để test thật: pin, âm thanh, FCM.
- Emulator **API 34, Google APIs** để test các hạn chế Android 12–14. Cấu hình giống PDA: màn 5 inch 720×1280, bật "Has hardware keyboard".
- Lệnh hay dùng:

```bash
adb logcat -s PdaAlertService PdaPollingService AlertDispatcher AlertPoller
adb shell dumpsys deviceidle force-idle        # giả lập Doze (tắt màn hình trước)
adb shell pm revoke com.example.andemo android.permission.POST_NOTIFICATIONS
adb shell am force-stop com.example.andemo
```

### Đối chiếu nhanh Java/Spring → Android

| Bạn đã biết | Android | Khác biệt quan trọng |
|---|---|---|
| Controller | Activity | Có vòng đời riêng; hệ thống tạo và hủy, không phải bạn |
| `@Async`, worker | Service, WorkManager | Bị hệ thống giới hạn chạy nền, mỗi version Android một kiểu |
| Event listener | BroadcastReceiver | Nhận sự kiện hệ thống (khởi động máy, có mạng…) |
| Feign / RestTemplate | Retrofit + OkHttp | `enqueue` (bất đồng bộ) và `execute` (đồng bộ, cấm trên main thread) |
| `application.properties` + bean | `AndroidManifest.xml` | Khai báo component và permission |
| Singleton bean | Biến static / singleton | **Process có thể bị kill bất cứ lúc nào**, state trong RAM mất hết |
| Request thread | Main (UI) thread | Chặn main thread quá 5 giây là ANR (app bị treo) |

---

## Tuần 1: Nền tảng

### Buổi 1: Project, Gradle, Manifest, Resource

- **Khái niệm:** cấu trúc project, Gradle và Android Gradle Plugin, `AndroidManifest.xml`, resource (`R.layout`, `mipmap`, qualifier như `-v26`), `BuildConfig`.
- **Đọc code:** `android/app/build.gradle` (cờ `-PapiBaseUrl`, `-PpdaChannel`), `AndroidManifest.xml`, `res/mipmap-anydpi-v26/`.
- **Bài tập:**
  1. Build và cài app lên máy realme bằng Android Studio, rồi bằng `./gradlew installDebug`.
  2. Mở tab "Merged Manifest" trong Android Studio, tìm chỗ `tools:replace` (bug B5) và giải thích vì sao cần nó.
- **Tự kiểm tra:** Vì sao `@mipmap/ic_launcher` phải có ở cả `mipmap/` lẫn `mipmap-anydpi-v26/`? `buildConfigField` sinh ra code ở đâu?

### Buổi 2: Activity, vòng đời, Intent

- **Khái niệm:** `onCreate` → `onStart` → `onResume` → `onPause` → `onStop` → `onDestroy`; Intent tường minh; back stack; `finish()`.
- **Đọc code:** `LoginActivity`, `MainActivity`, `ProductDetailActivity`.
- **Bài tập:**
  1. **S5:** nút Back không hoạt động khi thiếu barcode.
  2. **S4:** callback Retrofit chạy sau khi Activity đã bị hủy nên Glide crash. Cancel `Call` trong `onDestroy`.
  3. Xoay màn hình ở `ProductDetailActivity` và quan sát request chạy lại. Giải thích vì sao.
- **Tự kiểm tra:** Vì sao `finish()` trong `onCreate` không chạy tới `onResume`? Callback bất đồng bộ nguy hiểm thế nào với Activity?

### Buổi 3: Giao diện: layout, ListView, dialog

- **Khái niệm:** `LinearLayout`, `layout_weight`, `ListView` + `ArrayAdapter`, `AlertDialog`, `Handler` trên main thread.
- **Đọc code (nhánh polling):** `requester/FindPdaActivity.java`, `res/layout/activity_find_pda.xml`.
- **Bài tập:**
  1. Trong danh sách PDA, hiện thêm `deviceId` rút gọn (8 ký tự đầu) dưới tên máy.
  2. Tự làm mới danh sách PDA mỗi 10 giây khi màn hình đang hiển thị, và dừng khi rời màn hình (`onResume`/`onPause`).
  3. **S8:** popup zoom ảnh sản phẩm bằng Dialog.
- **Tự kiểm tra:** Vì sao `FindPdaActivity` gỡ `statusRunnable` trong `onPause`? `isDestroyed()` được kiểm tra ở đâu và để làm gì?

### Buổi 4: Gọi API: Retrofit, OkHttp, thread

- **Khái niệm:** interface Retrofit, `enqueue` và `execute`, interceptor, Gson, main thread và ANR.
- **Đọc code:** `api/ApiClient.java`, `api/AuthInterceptor.java`, `api/AlertApi.java`, `polling/AlertPoller.java` (dùng `execute` trên thread riêng).
- **Bài tập:**
  1. **S7:** đặt timeout OkHttp 30 giây. Chỉ log body ở bản debug.
  2. **S6:** decode ảnh gallery ở thread nền, có scale ảnh xuống.
- **Tự kiểm tra:** Gọi `execute()` trên main thread thì chuyện gì xảy ra? Interceptor khác Authenticator ở điểm nào?

### Buổi 5: Runtime permission

- **Khái niệm:** permission thường và permission nguy hiểm, Activity Result API, `shouldShowRequestPermissionRationale`, các thay đổi ở Android 13 (`POST_NOTIFICATIONS`).
- **Đọc code:** `QrScanActivity` (xin quyền camera).
- **Bài tập:** **A3:** xin `POST_NOTIFICATIONS` ở `MainActivity` trên Android 13+. Nếu user từ chối thì giải thích và mở trang cài đặt của app.
- **Tự kiểm tra:** Test trên emulator API 34: `adb shell pm revoke …` rồi mở app. Vì sao không có quyền này thì chuông kêu mà không tắt được?

---

## Tuần 2: Chạy nền, thông báo, âm thanh

### Buổi 6: Notification

- **Khái niệm:** notification channel (không đổi được importance sau khi tạo), `PendingIntent` (`FLAG_IMMUTABLE`), action, full-screen intent, `FLAG_INSISTENT`.
- **Đọc code:** `alert/PdaAlertService.java` (hai hàm `buildHighPriorityNotification` và `showFallbackNotification`), `command/CommandNotification.java` (nhánh websocket).
- **Bài tập:**
  1. **A1:** thêm `USE_FULL_SCREEN_INTENT`; trên Android 14 kiểm tra `canUseFullScreenIntent()` và dẫn user tới trang cấp quyền.
  2. **A13:** channel chính không phát âm thanh riêng. Nhớ đổi sang channel ID mới.
- **Tự kiểm tra:** Vì sao đổi importance thì phải đổi channel ID? Full-screen intent hiện toàn màn hình trong trường hợp nào, và chỉ hiện heads-up trong trường hợp nào?

### Buổi 7: Service và foreground service

- **Khái niệm:** vòng đời Service; `onStartCommand` được gọi nhiều lần trên cùng một instance; `START_STICKY` và `START_NOT_STICKY`; foreground service và `foregroundServiceType`; luật "startForegroundService thì phải gọi startForeground trong vài giây"; `HandlerThread`.
- **Đọc code:** `alert/PdaAlertService.java` (fix A4 và A6 có comment giải thích), `polling/PdaPollingService.java`.
- **Bài tập:** **A9:** khi alert dừng (Stop từ notification hoặc hết giờ) thì đóng `AlertActivity`. Gợi ý: service gửi broadcast nội bộ, Activity đăng ký nhận broadcast đó trong `onStart`/`onStop`.
- **Tự kiểm tra:** Vì sao bug A4 (leak MediaPlayer) xảy ra? Vì sao `PdaAlertService` gọi `startForeground` *trước* khi kiểm tra `requestId`?

### Buổi 8: Hạn chế chạy nền, Doze, alarm, wake lock

- **Khái niệm:** Doze và App Standby; hạn chế start foreground service từ background (Android 12+); `setAndAllowWhileIdle` và alarm chính xác; wake lock; `BOOT_COMPLETED`; nút Stop trong "Active apps" (Android 13+).
- **Đọc code (nhánh polling):** `polling/PdaPollingService.java` (3 nguồn kích hoạt poll), `polling/BootReceiver.java`, `docs/PDA_POLLING_DESIGN.md` mục 4 và 5.
- **Bài tập:**
  1. **Đo độ trễ thật trên máy realme** ở 3 trạng thái: đang dùng, màn hình tắt 5 phút, Doze (`force-idle`). Ghi kết quả vào tài liệu.
  2. **T2:** màn hình hướng dẫn tắt tối ưu pin (`isIgnoringBatteryOptimizations`, `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
- **Tự kiểm tra:** Vì sao `Handler.postDelayed` gần như dừng hẳn khi CPU ngủ? Vì sao không dùng `setExactAndAllowWhileIdle`?

### Buổi 9: Âm thanh

- **Khái niệm:** `AudioManager` và các stream, `AudioAttributes` (`USAGE_ALARM`), `MediaPlayer` (vòng đời prepare/start/release), DND.
- **Đọc code:** `PdaAlertService.forceMaxVolumeAndPlaySound`, `restoreAlarmVolume`.
- **Bài tập:**
  1. **A8:** thêm file âm thanh trong `res/raw` làm fallback khi máy không có nhạc báo thức.
  2. Lưu volume gốc vào `SharedPreferences` để khôi phục được kể cả khi process chết giữa lúc đang kêu (hạn chế đã ghi ở A6).
- **Tự kiểm tra:** Chế độ Silent có tắt `STREAM_ALARM` không? DND "Im lặng hoàn toàn" thì sao?

### Buổi 10: Lưu dữ liệu cục bộ

- **Khái niệm:** `SharedPreferences`, `apply` và `commit`, khi nào cần lưu bền thay vì để trong RAM. Biết thêm Room/DataStore là có, chưa cần dùng.
- **Đọc code:** `util/PreferenceManager.java`, `alert/ProcessedAlertStore.java` (nhánh polling).
- **Bài tập:** viết unit test JVM cho logic dọn requestId cũ. Gợi ý: tách phần logic thuần Java ra khỏi `SharedPreferences` để test được không cần Android.
- **Tự kiểm tra:** Vì sao `ProcessedAlertStore` phải lưu bền chứ không để trong `HashSet`? Vì sao `updateTokens` (nhánh websocket) dùng `commit()`?

---

## Tuần 3: Kết nối realtime, FCM, scanner

### Buổi 11: WebSocket và tự làm mới token

- **Khái niệm:** OkHttp WebSocket, ping/keepalive, reconnect với backoff và jitter, `ConnectivityManager.NetworkCallback`; `Authenticator` của OkHttp; refresh token xoay vòng.
- **Đọc code (nhánh websocket):** `websocket/PdaWebSocketService.java`, `api/TokenRefresher.java`, `api/TokenAuthenticator.java`, `docs/PDA_WEBSOCKET_DESIGN.md`. Phía server: `websocket/PdaSessionRegistry.java`, `service/RefreshTokenService.java`.
- **Bài tập:**
  1. So sánh độ trễ polling và WebSocket trên cùng máy (`-PpdaChannel=polling` và `-PpdaChannel=websocket`).
  2. Khi phiên đăng nhập hết (refresh token bị từ chối) mà app đang mở, tự chuyển về màn hình login. Đây là hạn chế đã ghi trong tài liệu WebSocket.
- **Tự kiểm tra:** Vì sao `TokenRefresher` phải `synchronized`? Vì sao WebSocket vẫn cần poll bắt kịp?

### Buổi 12: FCM

- **Khái niệm:** data message và notification message, priority, vòng đời token (`onNewToken`, `getToken`), cách FCM giao message khi app bị kill hoặc máy đang Doze.
- **Đọc code:** `fcm/MyFirebaseMessagingService.java`, `alert/AlertDispatcher.java`.
- **Bài tập:** **T1:** gọi `getToken()` sau khi login, gửi token lên server (API mock), gửi lại khi `onNewToken`. Cần tạo Firebase project và thêm `google-services.json`.
- **Tự kiểm tra:** Vì sao phải dùng data message? Sau force-stop thì FCM còn tới không?

### Buổi 13: Scanner cứng và BroadcastReceiver

- **Khái niệm:** receiver đăng ký trong manifest và đăng ký lúc runtime, `RECEIVER_EXPORTED` (Android 14), DataWedge (Zebra), keyboard wedge.
- **Đọc code:** `QrScanActivity.extractBarcodeFromIntent`, tracker mục S1, S2.
- **Bài tập:**
  1. **S1:** nhận barcode qua broadcast đăng ký lúc runtime. Test bằng lệnh `adb shell am broadcast -a com.example.andemo.SCAN --es com.symbol.datawedge.data_string 8934567890123`.
  2. **S2 + S3:** nhận input kiểu keyboard wedge (bật "Has hardware keyboard" trên emulator rồi gõ barcode + Enter), validate barcode, chống scan trùng.
- **Tự kiểm tra:** Vì sao code cũ (activity `exported=false`, không có intent-filter) không bao giờ nhận được barcode?

### Buổi 14: Gom lại và test end-to-end

- **Bài tập:** chạy toàn bộ luồng với 2 máy (realme làm PDA, emulator làm requester), hoặc dùng web quản lý (`/pda-finder.html`). Đi qua bảng kịch bản test trong `PDA_POLLING_DESIGN.md` và `PDA_WEBSOCKET_DESIGN.md`, ghi lại kết quả.
- **Tự kiểm tra (câu tổng kết):** giải thích cho một người khác, không nhìn tài liệu: "Khi quản lý bấm Tìm trên web, chuyện gì xảy ra trên server và trên PDA, từng bước, và mỗi bước có thể hỏng vì lý do gì?"

---

## Tài liệu gợi ý

Trên developer.android.com, tìm theo tên:
- "The activity lifecycle", "Tasks and the back stack"
- "Request runtime permissions"
- "Create and manage notification channels", "Time-sensitive notifications"
- "Foreground services overview", "Foreground service types", "Restrictions on starting a foreground service from the background"
- "Optimize for Doze and App Standby", "Schedule alarms"
- "Behavior changes" của Android 12, 13, 14. Ba trang này giải thích phần lớn bug trong tracker.

Firebase: "About FCM messages", "Manage FCM registration tokens".
OkHttp: trang "Recipes" (có ví dụ WebSocket và Authenticator).
