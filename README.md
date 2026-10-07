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

- Mở folder `android` bằng Android Studio (cần Android SDK 34). Build bằng **Gradle 9.3.1 + Android Gradle Plugin 9.1.1**,
  chạy được với JDK 17 đến 25 (JBR đi kèm Android Studio là đủ). Android Studio gợi ý nâng Gradle / AGP thì bỏ qua:
  AGP 9.x đòi Gradle cao hơn (AGP 9.3 cần Gradle 9.5, AGP 9.4 cần Gradle 9.6).
- Hoặc build bằng command line: `cd android && ./gradlew assembleDebug`
  (APK nằm ở `android/app/build/outputs/apk/debug/app-debug.apk`)
- Không có `android/app/google-services.json` app vẫn build được, chỉ là FCM không hoạt động.
  File này là config riêng của từng Firebase project nên không commit (đã có trong `.gitignore`).
- Backend URL: thuộc tính Gradle `apiBaseUrl` (mặc định là server Render). Ví dụ thêm
  `apiBaseUrl=http://10.0.2.2:8081/` vào `gradle.properties` để máy ảo gọi mock server trên máy tính.
  Chi tiết: `docs/LOCAL_SETUP_GUIDE.md` mục 5.

## Tài liệu

**Migrate Nexacro 17 mobile → Android (Java)**: bắt đầu ở [**Mục lục tài liệu**](docs/nexacro-migration/README.md) (mọi file migrate / học / task hủy hàng đều link từ đó).
- Đang học: [lộ trình 2 tuần](docs/nexacro-migration/LEARNING_PLAN_2_WEEKS.md)
- Đang làm 1 màn: [công thức 8 bước + khung code](docs/nexacro-migration/HOW_TO_CODE_A_SCREEN.md)
- Tập build / run project REST API giống dự án (Gradle 7, Java 8): [`restapi-demo/`](restapi-demo/README.md)

**PDA Finder và project chung** (`docs/`)
- [LOCAL_SETUP_GUIDE.md](docs/LOCAL_SETUP_GUIDE.md): **setup và test trên máy local** (mock server, web quản lý, máy ảo / máy thật)
- [FCM_SETUP.md](docs/FCM_SETUP.md): setup Firebase và test nhận lệnh qua FCM
- [DEPLOY_RENDER.md](docs/DEPLOY_RENDER.md): deploy backend lên Render
- [ANDROID_LEARNING_PLAN.md](docs/ANDROID_LEARNING_PLAN.md): 14 buổi học Android qua project PDA
- [ANDROID_FIX_TRACKER.md](docs/ANDROID_FIX_TRACKER.md): bug Android cần fix
- Thiết kế: [PDA_FINDER_ALERT_REQUIREMENTS.md](docs/PDA_FINDER_ALERT_REQUIREMENTS.md), [PDA_FINDER_MECHANISM_OPTIONS.md](docs/PDA_FINDER_MECHANISM_OPTIONS.md), [PDA_POLLING_DESIGN.md](docs/PDA_POLLING_DESIGN.md), [PDA_WEBSOCKET_DESIGN.md](docs/PDA_WEBSOCKET_DESIGN.md)

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
