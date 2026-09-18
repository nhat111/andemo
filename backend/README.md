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

### Tài khoản mẫu
| Username | Password | Role  |
|----------|----------|-------|
| admin    | 123456   | ADMIN |
| user     | 123456   | USER  |

## Deploy lên Render (miễn phí)

1. Vào [render.com](https://render.com) → New → Web Service
2. Connect repo `nhat111/andemo`
3. Render sẽ tự detect `render.yaml` + Dockerfile
4. Hoặc set thủ công:
   - **Root Directory**: `backend`
   - **Dockerfile Path**: `./Dockerfile`
5. Deploy → lấy URL public

Sau đó đổi `BASE_URL` trong Android `ApiClient.java` thành URL Render.
