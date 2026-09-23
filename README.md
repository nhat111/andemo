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

- Mở folder `android` bằng Android Studio
- Emulator: dùng `http://10.0.2.2:8080`
- Máy thật: đổi IP máy tính trong `ApiClient.java`

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
