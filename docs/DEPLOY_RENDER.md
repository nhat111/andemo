# Deploy backend + web quản lý lên Render

> Áp dụng cho nhánh `claude/pda-finder-websocket` (backend Spring có WebSocket, refresh token, web quản lý `/pda-finder.html`).
> Cập nhật: 2026-09-28

Sau khi deploy, có 3 thành phần dùng chung 1 server:

| Thành phần | Địa chỉ | Ai dùng |
|---|---|---|
| Web quản lý | `https://<service>.onrender.com/pda-finder.html` (hoặc `/`) | Quản lý (ADMIN) gửi lệnh tìm PDA |
| API + WebSocket | `https://<service>.onrender.com/api/…`, `wss://<service>.onrender.com/ws/pda` | App Android trên PDA |
| Health check | `https://<service>.onrender.com/api/health` | Render |

---

## 1. Chọn nhánh Render sẽ build

Render build lại mỗi khi nhánh được theo dõi có commit mới. Có 2 cách:

- **Demo nhanh:** Render Dashboard → service → **Settings → Build & Deploy → Branch** → chọn `claude/pda-finder-websocket`.
- **Chính thức:** merge nhánh này vào `main` (qua Pull Request), Render đang theo dõi `main` thì tự deploy.

Service tạo tay (New → Web Service) dùng cấu hình: **Runtime** Docker, **Root Directory** `backend`, **Dockerfile Path** `./Dockerfile`.

Nếu service được tạo từ Blueprint (`render.yaml`), các thay đổi trong `render.yaml` (health check, biến môi trường) được áp dụng khi sync Blueprint. Nếu service tạo tay, cần đặt tay các mục ở bước 2 và 3.

## 2. Health check

`render.yaml` đã đổi `healthCheckPath` thành **`/api/health`**. Giá trị cũ `/api/auth/login` chỉ nhận POST, nên Render gọi GET sẽ nhận 405 và coi là server không khỏe.

Service tạo tay: **Settings → Health Check Path** = `/api/health`.

## 3. Biến môi trường

| Biến | Bắt buộc | Giá trị |
|---|---|---|
| `JWT_SECRET` | Có | Chuỗi ngẫu nhiên ≥ 32 ký tự. Blueprint tự sinh (`generateValue`). **Đổi secret = mọi token hiện có mất hiệu lực, mọi thiết bị phải login lại.** |
| `APP_SEED_PASSWORD` | **Có khi deploy công khai** | Mật khẩu cho tài khoản mẫu `admin`, `user`, `user2`. Không đặt thì là `123456`, ai cũng đăng nhập được web quản lý. Chỉ có tác dụng khi database chưa có user nào |
| `SPRING_DATASOURCE_URL` | Khuyến nghị | `jdbc:postgresql://<host>:5432/<database>` (xem bước 4) |
| `SPRING_DATASOURCE_USERNAME` | Đi cùng URL | User database |
| `SPRING_DATASOURCE_PASSWORD` | Đi cùng URL | Mật khẩu database |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Không (chỉ khi dùng FCM) | Toàn bộ nội dung file service account JSON của Firebase. Trống hoặc sai thì FCM tắt, backend vẫn chạy. Xem [FCM_SETUP.md](FCM_SETUP.md) |
| `JAVA_OPTS` | Không | Đã có trong Blueprint: `-Xms128m -Xmx384m -XX:+UseContainerSupport` |

**Không tạo biến với giá trị rỗng** cho `SPRING_DATASOURCE_*`: URL rỗng làm backend không khởi động được. Không dùng Postgres thì đừng tạo 3 biến này.

## 4. Database bền (khuyến nghị)

Không đặt `SPRING_DATASOURCE_*` thì backend dùng **H2 trong RAM**. Mỗi lần deploy, restart, hoặc server free tier ngủ dậy:
- Mất lịch sử tìm kiếm và danh sách PDA.
- **Mất toàn bộ refresh token**, nên mọi PDA bị logout trong vòng tối đa 1 giờ (khi access token hết hạn).

Cách bật Postgres:
1. Tạo database Postgres: Render Postgres (Dashboard → New → PostgreSQL), hoặc dịch vụ Postgres khác. **Kiểm tra chính sách gói free trên trang giá của Render:** database free có thể có giới hạn thời gian sử dụng.
2. Render cho chuỗi kết nối dạng `postgresql://USER:PASSWORD@HOST/DATABASE`. Spring cần tách ra:
   - `SPRING_DATASOURCE_URL=jdbc:postgresql://HOST:5432/DATABASE`. Dùng **Internal Database URL** nếu database và service cùng region.
   - `SPRING_DATASOURCE_USERNAME=USER`
   - `SPRING_DATASOURCE_PASSWORD=PASSWORD`
3. Deploy lại. Bảng (`users`, `refresh_token`, `pda_device`, `pda_alert`) được tạo tự động (`ddl-auto=update`); user mẫu chỉ được tạo khi bảng `users` còn trống.

Đã kiểm tra với Postgres 16 chạy local: tạo lệnh, restart backend, lịch sử / PDA / refresh token vẫn còn, user mẫu không bị tạo trùng.

## 5. Kiểm tra sau khi deploy

```bash
S=https://<service>.onrender.com

curl $S/api/health                                   # {"status":"UP"}
curl -X POST $S/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<APP_SEED_PASSWORD>"}'   # có token + refreshToken
```

Sau đó mở `$S/pda-finder.html` trên trình duyệt và login `admin`.

## 6. App Android trỏ vào Render

```bash
cd android
./gradlew installDebug -PapiBaseUrl=https://<service>.onrender.com/                       # WebSocket (mặc định)
./gradlew installDebug -PapiBaseUrl=https://<service>.onrender.com/ -PpdaChannel=polling  # polling
```

Mặc định app trỏ tới `https://andemo.onrender.com/` (`app/build.gradle`). Nếu URL service khác, truyền `-PapiBaseUrl` hoặc sửa giá trị mặc định đó. Lưu ý Blueprint đặt tên service là `andemo-backend`.

Trên PDA: login bằng `user` (mật khẩu `APP_SEED_PASSWORD`). Trên web: PDA hiện trong bảng "Chọn PDA cần tìm" sau vài giây.

## 7. Lưu ý về gói free của Render

- **Server ngủ sau khoảng 15 phút không có request**, lần gọi đầu tiên sau đó mất khoảng 1 phút để khởi động. PDA đang chạy app tự gửi request định kỳ (polling mỗi ~30 giây; WebSocket có poll kiểm tra mỗi 3 phút) nên server thường không ngủ khi có PDA hoạt động.
- Mỗi lần server khởi động lại, mọi kết nối WebSocket bị ngắt; app tự kết nối lại (backoff tối đa 60 giây).
- Server chạy 1 instance: đúng với thiết kế hiện tại (registry WebSocket nằm trong RAM của instance đó). Scale nhiều instance cần Redis pub/sub.
- Giới hạn giờ chạy và băng thông hằng tháng của gói free: xem trang giá của Render.

## 8. Checklist trước khi demo cho khách

- [ ] `APP_SEED_PASSWORD` đã đặt (không còn `123456`)
- [ ] Đã bật Postgres, hoặc chấp nhận mất dữ liệu khi restart
- [ ] `/api/health` trả `UP`
- [ ] Web quản lý login được bằng `admin`
- [ ] App trên PDA login `user`, PDA hiện trên web với trạng thái Online
- [ ] Bấm "Tìm" trên web → PDA đổ chuông → bấm "Dừng Alert" → web hiện ✅
- [ ] Nếu PDA là Android 13+: đã cấp quyền thông báo cho app (bug A3 chưa sửa, app chưa tự xin quyền)
