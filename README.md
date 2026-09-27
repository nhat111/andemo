# Andemo - Android + Java Backend (Login + JWT + Role Menu)

Pet project: Login with JWT, ẩn/hiện menu theo role (ADMIN / USER).

## Cấu trúc

```
backend/          # Spring Boot 3 + JWT
android/          # Android Java app
docs/             # Tài liệu requirement & design
```

## Tài khoản mẫu

| Username | Password | Role  |
|----------|----------|-------|
| admin    | 123456   | ADMIN |
| user     | 123456   | USER  |

## Chạy Backend

```bash
cd backend
./mvnw spring-boot:run
```

API: `http://localhost:8080/api/auth/login`

## Chạy Android

- Mở folder `android` bằng Android Studio (cần JDK 17+ và Android SDK 34)
- Hoặc build bằng command line: `cd android && ./gradlew assembleDebug`
  (APK nằm ở `android/app/build/outputs/apk/debug/app-debug.apk`)
- Không có `android/app/google-services.json` app vẫn build được, chỉ là FCM không hoạt động.
  File này là config riêng của từng Firebase project nên không commit (đã có trong `.gitignore`).
- Backend URL cấu hình trong `ApiClient.java` (`BASE_URL`).
  Muốn chạy backend local: emulator dùng `http://10.0.2.2:8080/`, máy thật dùng IP máy tính.

## Tính năng

- Login → nhận JWT + role
- Lưu token local (SharedPreferences)
- ADMIN thấy menu Admin, USER chỉ thấy menu User
- Logout xóa token
- **PDA Finder Alert (FCM)** – nhận lệnh tìm PDA từ xa, phát alert max volume

## PDA Finder Alert (FCM)

Chi tiết requirement và cách implement xem tại:

📄 [docs/PDA_FINDER_ALERT_REQUIREMENTS.md](docs/PDA_FINDER_ALERT_REQUIREMENTS.md)

### Setup nhanh FCM

1. Tạo project Firebase → thêm Android app (`com.example.andemo`)
2. Tải `google-services.json` → bỏ vào `android/app/`
3. Sync Gradle
4. Chạy app, lấy token từ Logcat (`MyFirebaseMsgService`)
5. Gửi **Data message** từ Firebase Console để test
