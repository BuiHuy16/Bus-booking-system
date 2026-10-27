# 6. Hướng dẫn chạy BusBooking (Running Guide)


## 6.1. Các thành phần cần chạy

- **Spring Boot backend:** REST API, mặc định ở `http://localhost:8085` khi chạy local với cấu hình hiện dùng.
- **PostgreSQL:** lưu tài khoản, danh mục, chuyến xe và booking; **Flyway** thực thi/kiểm tra migration khi ứng dụng khởi động.
- **SMTP:** dùng để gửi mã xác thực email (OTP). Có thể dùng **Mailpit** để kiểm tra email trong môi trường phát triển.
- **Swagger/OpenAPI:** thử các endpoint; trong Pha 1 hệ thống có **32 REST API** theo phạm vi đã chốt.

## 6.2. Yêu cầu môi trường

| Công cụ | Yêu cầu / ghi chú |
|---|---|
| Git | Clone source từ GitHub |
| Docker Desktop + Docker Compose | Cách chạy ưu tiên; bật Linux containers |
| JDK 21 | Cần khi build/chạy backend trực tiếp trên Windows |
| Maven | **Không cần cài riêng** nếu dùng Maven Wrapper (`mvnw.cmd`) |
| PostgreSQL | Có thể chạy bằng Docker; không cần cài trên Windows nếu Compose đã cung cấp database |

Kiểm tra nhanh:

```powershell
java -version
.\mvnw.cmd -v
docker version
docker compose version
```

## 6.3. Chuẩn bị cấu hình

1. Clone repository, mở PowerShell ở thư mục `busbooking/`.
2. Kiểm tra `docker-compose.yml`, `.env.example` và `src/main/resources/application*.properties` trước khi chạy.
3. Nếu Compose sử dụng `.env`, sao chép mẫu và **tự điền các giá trị còn thiếu**, không đưa mật khẩu/secret thật lên GitHub:

   ```powershell
   if (-not (Test-Path .env)) { Copy-Item .env.example .env }
   ```

   Chỉ dùng lệnh này khi `.env.example` có tồn tại; `.env` là cấu hình cá nhân và phải được `.gitignore` loại trừ.
4. Đảm bảo secret JWT và OTP **khác nhau**, là chuỗi Base64 giải mã ra **ít nhất 32 byte**. Với cấu hình đã được dùng trước đây, các biến quan trọng gồm:

   | Nhóm | Biến tiêu biểu |
   |---|---|
   | Database | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
   | JWT | `JWT_SECRET`, `JWT_EXPIRATION_MS`, `JWT_ISSUER` |
   | OTP | `EMAIL_OTP_SECRET` |
   | SMTP | `SMTP_HOST`, `SMTP_PORT`, `SMTP_FROM`, `SMTP_AUTH`, `SMTP_STARTTLS` |
   | Docs / môi trường | `SPRING_PROFILES_ACTIVE`, `API_DOCS_ENABLED` |

   **Chú ý:** `.env.example` có thể được cập nhật theo bản hiện tại; luôn ưu tiên các tên biến đang được Spring Boot/Compose thực sự sử dụng. Spring Boot khi chạy **trực tiếp bằng Maven không tự đọc `.env`**.

## 6.4. Cách 1: Chạy bằng Docker Compose (khuyến nghị)

**Bước 1.** Kiểm tra Compose hợp lệ và xem tên service thực tế:

```powershell
docker compose config --quiet
docker compose config --services
```

Nếu repository chưa có `docker-compose.yml` hoặc lệnh kiểm tra báo lỗi, **không tiếp tục với lệnh khởi chạy**; kiểm tra lại bản bàn giao và cấu hình cần thiết.

**Bước 2.** Build và khởi động các service được định nghĩa trong Compose:

```powershell
docker compose up -d --build
docker compose ps
```

**Bước 3.** Xem log khởi động để kiểm tra kết nối database và Flyway:

```powershell
docker compose logs --tail=100
docker compose logs -f
```

Thoát chế độ xem log bằng `Ctrl+C` (không dừng container).

**Bước 4.** Mở Swagger hoặc thử API theo mục 6.6. Nếu Compose có đổi cổng publish của backend, dùng **cổng host được hiển thị trong `docker compose ps`** thay vì mặc định 8085.

**Dừng hệ thống** (không xóa database volume):

```powershell
docker compose down
```

> **Không dùng `docker compose down -v`** khi muốn giữ dữ liệu. Nếu Compose chỉ cung cấp PostgreSQL/Mailpit mà **không có backend service**, hãy chạy hai service đó rồi dùng cách 2 để khởi động Java.

## 6.5. Cách 2: Chạy Spring Boot trực tiếp trên Windows

Dùng cách này khi cần debug backend bằng VS Code/IntelliJ hoặc khi Compose chỉ chạy các dịch vụ phụ trợ.

**Bước 1.** Khởi động PostgreSQL và SMTP thử nghiệm trước; kiểm tra host/port đang publish:

```powershell
docker ps
```

Nếu Mailpit chưa được Compose cung cấp, có thể chạy **một container Mailpit riêng**, miễn là cổng 1025/8025 chưa bị chiếm:

```powershell
docker run -d --name busbooking-auth-mailpit `
  -p 127.0.0.1:1025:1025 `
  -p 127.0.0.1:8025:8025 `
  axllent/mailpit:v1.27.8
```

Mailpit UI: `http://localhost:8025`. Nếu container đã tồn tại, dùng `docker start busbooking-auth-mailpit` thay vì `docker run` lần nữa.

**Bước 2.** Khai báo biến môi trường cho tiến trình PowerShell. Thay `<PORT_DB>` theo cổng host hiện tại từ `docker ps`, và cung cấp mật khẩu/secret **của môi trường phát triển**, không đưa chúng vào source code:

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:DB_URL = "jdbc:postgresql://localhost:<PORT_DB>/busbooking"
$env:DB_USERNAME = "busbooking_user"
$env:DB_PASSWORD = Read-Host "Mật khẩu PostgreSQL dev"

# Tạo hai secret độc lập dùng trong phiên demo.
# Giữ giá trị ổn định nếu muốn JWT/OTP còn hiệu lực sau restart.
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$jwtBytes = New-Object byte[] 32
$otpBytes = New-Object byte[] 32
$rng.GetBytes($jwtBytes)
$rng.GetBytes($otpBytes)
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$env:EMAIL_OTP_SECRET = [Convert]::ToBase64String($otpBytes)
$rng.Dispose()
$env:JWT_ISSUER = "busbooking"
$env:JWT_EXPIRATION_MS = "86400000"

$env:SMTP_HOST = "127.0.0.1"
$env:SMTP_PORT = "1025"
$env:SMTP_AUTH = "false"
$env:SMTP_STARTTLS = "false"
$env:SMTP_FROM = "noreply@example.com"
$env:API_DOCS_ENABLED = "true"
$env:BOOTSTRAP_SUPER_ADMIN_ENABLED = "false"
```

**Bước 3.** Build và chạy:

```powershell
.\mvnw.cmd -DskipTests clean package
.\mvnw.cmd spring-boot:run
```

Ứng dụng sử dụng cấu hình `dev` trong phiên PowerShell trên. Khi chạy bằng IDE, cần khai báo **cùng các biến môi trường** trong Run Configuration. Nếu PostgreSQL/SMTP chạy **trong container** và backend cũng chạy **trong container**, không dùng `localhost` để kết nối container khác; thay bằng hostname/service tương ứng của Docker network.

## 6.6. Kiểm tra sau khi khởi chạy

Với backend local cổng 8085 và tài liệu API được bật:

| Thành phần | URL |
|---|---|
| Swagger UI | http://localhost:8085/swagger-ui/index.html |
| OpenAPI JSON | http://localhost:8085/v3/api-docs |
| Base API | http://localhost:8085/api |
| Mailpit (nếu có) | http://localhost:8025 |

Kiểm tra HTTP và xuất đặc tả API từ **ứng dụng đang chạy**:

```powershell
Invoke-WebRequest http://localhost:8085/v3/api-docs `
  -OutFile docs/openapi.json
```

Swagger có thể bị tắt ngoài profile `dev`/`test` nếu chưa bật `API_DOCS_ENABLED`; **404 từ Swagger không đồng nghĩa backend bị lỗi**.

**Luồng kiểm tra chức năng tối thiểu:**

1. `POST /api/auth/register` — đăng ký một email thử nghiệm; chưa nhận JWT.
2. Mở Mailpit hoặc hộp thư đã cấu hình để lấy OTP; gọi `POST /api/auth/verify-email`.
3. `POST /api/auth/login` — nhận JWT, dùng Swagger **Authorize** với Bearer token.
4. `GET /api/auth/me`, `GET /api/locations`, `GET /api/trips` — kiểm tra xác thực và dữ liệu công khai.
5. `POST /api/bookings` — tạo booking cho chuyến còn ghế; kiểm tra xem/hủy booking theo quyền.
6. Dùng tài khoản có quyền phù hợp để thử API quản trị; `USER` không được truy cập endpoint chỉ dành cho `ADMIN`/`SUPER_ADMIN`.

Chi tiết request/response, quyền và 32 endpoint: [`04-api-design.md`](04-api-design.md) hoặc `docs/openapi.json` sau khi xuất.

## 6.7. Kiểm thử và benchmark

```powershell
# Chạy các bài kiểm thử Maven; cần môi trường test và database test độc lập
.\mvnw.cmd test

# Build JAR để đóng gói/bàn giao
.\mvnw.cmd -DskipTests clean package
```

**Không trỏ integration test vào database development có dữ liệu cần giữ.** Kiểm tra `application-test.properties`, `TEST_DB_URL` và các biến liên quan trước khi chạy test.

Baseline hiệu năng GET Trip / POST Booking Pha 1 trên Kaggle CPU, script Locust, dữ liệu giả lập và kết quả nằm ở [`../load-tests/`](../load-tests/). Bài benchmark Kaggle chạy Java + PostgreSQL + Locust trong notebook, **không yêu cầu Docker bên trong Kaggle**.

## 6.8. Xử lý lỗi thường gặp

| Hiện tượng | Kiểm tra / xử lý |
|---|---|
| `Connection refused` PostgreSQL | `docker compose ps` hoặc `docker ps`; kiểm tra đúng **cổng host**, database, user, password và network |
| Flyway checksum mismatch / missing table | Đối chiếu migration **V1–V16** với lịch sử DB; **không tự ý sửa migration cũ, tắt validation hoặc chạy `flyway repair`** để che lỗi |
| Backend không lên vì JWT/OTP secret | Kiểm tra hai secret Base64 khác nhau, tối thiểu 32 byte sau giải mã |
| Không nhận OTP | Kiểm tra SMTP host/port, Mailpit UI và log ứng dụng; đảm bảo Mailpit đang chạy |
| Swagger trả 404 | Kiểm tra profile `dev`, `API_DOCS_ENABLED` và cổng publish backend |
| HTTP `401` / `403` | Kiểm tra JWT, trạng thái xác minh email, role và ownership; đây có thể là phản hồi phân quyền đúng |
| HTTP `429` khi thử auth | Rate limit đăng ký/đăng nhập/OTP; chờ hết cửa sổ giới hạn thay vì gọi liên tục |
| Port 8085/5432/1025/8025 bị chiếm | Dùng `docker ps`, `Get-NetTCPConnection -State Listen`; điều chỉnh cổng phù hợp trong cấu hình |
| Chạy Java trong container không kết nối được DB | JDBC URL không được dùng `localhost` để chỉ container PostgreSQL khác; dùng tên service/network thực tế |

