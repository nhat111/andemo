# Backend - Andemo

Spring Boot 3 + JWT + Role-based auth

## Chạy local (Maven)

```bash
./mvnw spring-boot:run
```

## Chạy bằng Docker (khuyên dùng)

```bash
# Build + run
docker compose up --build

# Hoặc
docker build -t andemo-backend .
docker run -p 8080:8080 andemo-backend
```

API: http://localhost:8080/api/auth/login

Auth:
- `POST /api/auth/login` → `{token, refreshToken, role, username}`. Access token sống 1 giờ.
- `POST /api/auth/refresh` body `{"refreshToken": "…"}` → cặp token mới (refresh token xoay vòng, sống 30 ngày).
- `POST /api/auth/logout` body `{"refreshToken": "…"}` → thu hồi refresh token.
- Token sai / hết hạn / thiếu → 401; không có quyền → 403.

PDA Finder (lệnh tìm PDA, WebSocket `/ws/pda`): xem `docs/PDA_WEBSOCKET_DESIGN.md`.

Chạy test: `mvn test`

### Tài khoản mẫu
| Username | Password | Role  |
|----------|----------|-------|
| admin    | 123456   | ADMIN |
| user     | 123456   | USER  |

## Deploy lên Render (miễn phí)

Xem hướng dẫn đầy đủ: [`docs/DEPLOY_RENDER.md`](../docs/DEPLOY_RENDER.md) (biến môi trường, database bền, health check, checklist trước khi demo).

Tóm tắt: Render build theo `render.yaml` + `backend/Dockerfile`; health check `/api/health`; **bắt buộc đặt `APP_SEED_PASSWORD`** khi deploy công khai; nên đặt `SPRING_DATASOURCE_*` để dùng Postgres. Web quản lý: `https://<service>.onrender.com/pda-finder.html`.
