# 3. Kiến trúc hệ thống


## 3.1. Tổng quan

BusBooking là backend hỗ trợ quản lý tài khoản, xác thực email, danh mục nhà xe/địa điểm/phương tiện/tuyến đường, tìm kiếm chuyến xe và đặt vé. Ứng dụng được xây dựng bằng **Java 21, Spring Boot**, giao tiếp qua **REST/JSON**, lưu trữ trên **PostgreSQL** và quản lý thay đổi schema bằng **Flyway**.

Về tổ chức phần mềm, hệ thống kết hợp:

- **Kiến trúc phân lớp:** phân chia trách nhiệm thành các lớp tiếp nhận API, xử lý nghiệp vụ và truy cập dữ liệu.
- **Ports và Adapters:** nghiệp vụ khai báo các *port* (interface) để làm việc với repository, xác thực và gửi thông báo; các lớp hạ tầng cung cấp implementation tương ứng.

Đây là **một ứng dụng backend** được phân tách thành các package. Client có thể là Postman hoặc một giao diện web/mobile sử dụng API; tài liệu này không mặc định frontend đã được triển khai.

```mermaid
flowchart TB
    C["Client: Postman / Web / Mobile"] -->|HTTP / JSON| S["Spring Security / HTTP Filters"]
    S --> A["API: Controllers, DTOs, DTO Mappers"]
    A --> B["Business: Services, Commands, Rules"]
    B --> D["Domain: Models, Enums, Exceptions"]
    B --> P["Outbound Ports: Repository / Security / Notification"]
    PA["Persistence Adapters: Spring Data JPA, Mappers"] -.->|implements| P
    SA["Security Adapters: JWT, BCrypt, HMAC"] -.->|implements| P
    EA["SMTP Email Adapter"] -.->|implements| P
    PA --> DB[(PostgreSQL)]
    EA --> SMTP["SMTP Server / Mailpit (môi trường test)"]
```

**Lưu ý:** Sơ đồ trên thể hiện cả luồng tương tác và quan hệ triển khai interface. Các mũi tên `implements` **không** có nghĩa Business phụ thuộc vào lớp adapter. Nhánh JPA được minh họa; `EmailVerificationRepositoryAdapter` và `RoleManagementRepositoryAdapter` còn dùng `JdbcTemplate` trực tiếp.

## 3.2. Các thành phần và trách nhiệm

| Thành phần | Package chính | Vai trò |
|---|---|---|
| API | `api/controller`, `api/dto`, `api/mapper`, `api/validation` | Nhận request, xác thực dữ liệu ở ranh giới HTTP, ánh xạ DTO và trả response |
| Business | `business/service`, `business/command`, `business/result` | Điều phối use case, kiểm tra quy tắc nghiệp vụ, tạo kết quả độc lập với định dạng HTTP |
| Outbound Ports | `business/port/repository`, `business/port/security`, `business/port/notification` | Định nghĩa các hợp đồng cần dùng từ hạ tầng |
| Domain | `domain/model`, `domain/enums`, `domain/exception` | Mô hình nghiệp vụ, trạng thái và ngoại lệ nghiệp vụ |
| Persistence | `data/adapter`, `data/repository`, `data/entity`, `data/mapper` | Hiện thực repository port bằng JPA/mapping domain ↔ entity; OTP và quản lý role còn dùng `JdbcTemplate` |
| Security | `security`, `security/ratelimit` | Xác thực JWT, mã hóa mật khẩu, HMAC OTP, giới hạn tần suất và xử lý lỗi truy cập |
| Notification | `notification` | Adapter gửi email xác minh qua SMTP |
| Cross-cutting | `config`, `exception` | Cấu hình ứng dụng, OpenAPI, biểu diễn lỗi HTTP và xử lý exception tập trung |
| Persistence schema | `src/main/resources/db/migration` | Các migration Flyway cho PostgreSQL (V1–V16 trong cây source được cung cấp) |

### API layer

Các controller hiện có được tổ chức theo nhóm nghiệp vụ:

- **Tài khoản/quyền:** `AuthController`, `UserController`, `RoleManagementController`.
- **Đọc dữ liệu công khai hoặc theo quyền:** `LocationController`, `TripController`, `BookingController`.
- **Quản trị:** `AdminBookingController`, `AdminLocationController`, `AdminOperatorController`, `AdminRouteController`, `AdminTripController`, `AdminVehicleController`.

Mỗi controller làm việc với DTO của `api/dto` và mapper của `api/mapper`. **Không có** một `AdminController` chung trong cây source hiện tại; chức năng quản trị được tách theo tài nguyên. Mô tả đường dẫn/HTTP method chi tiết thuộc `04-api-design.md` và file OpenAPI xuất từ ứng dụng.

### Business và Domain

Các service hiện diện trong `business/service` gồm `AuthService`, `EmailVerificationService`, `UserService`, `RoleManagementService`, `BootstrapSuperAdminService`, `OperatorService`, `LocationService`, `VehicleService`, `RouteService`, `TripService`, `BookingService`, cùng các chính sách đầu vào như `AuthInputPolicy` và `OperatorInputPolicy`.

- **Business service** thực hiện luồng use case, phối hợp các port và áp dụng điều kiện nghiệp vụ.
- **Domain model / enum** biểu diễn `User`, `Operator`, `Vehicle`, `Route`, `Trip`, `Booking` cùng các trạng thái liên quan.
- **Domain exception** biểu diễn các tình huống nghiệp vụ, ví dụ `InsufficientSeatsException`, `TripConflictException`, `ForbiddenOperationException`, `DuplicateRouteException`.

Những quy tắc như quyền sở hữu booking, số ghế khả dụng hoặc trạng thái chuyến không nên được viết lặp lại trong controller hay repository adapter.

## 3.3. Quy tắc phụ thuộc (Dependency Rule)

Cần phân biệt **luồng gọi lúc chạy** và **phụ thuộc khi biên dịch**:

**Luồng gọi khi xử lý HTTP:** Sơ đồ minh họa nhánh Spring Data JPA; adapter OTP/role có nhánh JDBC trực tiếp tới PostgreSQL.

```mermaid
flowchart TD
    A["Client"]
    B["Security Filters"]
    C["Controller"]
    D["Business Service"]
    E["Repository Port"]
    F["Persistence Adapter"]
    G["Spring Data JPA"]
    H[("PostgreSQL")]

    A --> B
    B --> C
    C --> D
    D --> E
    E --> F
    F --> G
    G --> H
```

**Phụ thuộc ở mã nguồn:**

```mermaid
flowchart TD
    API["api/controller"]
    SERVICE["business/service"]
    PORT["business/port"]
    DOMAIN["domain"]

    ADAPTER["data/adapter"]
    REPO["data/repository"]
    JPA["Spring Data JPA"]

    SECURITY["security"]
    NOTIFICATION["notification"]

    API --> SERVICE
    SERVICE --> PORT
    SERVICE --> DOMAIN

    ADAPTER -->|implements| PORT
    ADAPTER --> DOMAIN
    ADAPTER --> REPO
    REPO --> JPA

    SECURITY -.->|implements relevant ports| PORT
    NOTIFICATION -.->|implements notification port| PORT
```

Ví dụ có thể đối chiếu trong source:

| Vai trò | Class/interface |
|---|---|
| Repository port | `business/port/repository/BookingRepository.java` |
| Implementation | `data/adapter/BookingRepositoryAdapter.java` |
| Truy cập JPA | `data/repository/SpringDataBookingRepository.java` |
| Persistence entity | `data/entity/BookingEntity.java` |
| Mapping | `data/mapper/BookingPersistenceMapper.java` |

`BookingService` sử dụng hợp đồng repository của tầng nghiệp vụ thay vì cần biết trực tiếp cơ chế JPA hoặc PostgreSQL. Cách tách này giúp thay implementation lưu trữ và viết kiểm thử bằng fake/mock repository dễ hơn. Việc tất cả class đều tuân thủ quy tắc này cần được kiểm tra bằng import/dependency thực tế, không thể kết luận chỉ từ cấu trúc thư mục.

## 3.4. Các thành phần chức năng

```mermaid
flowchart LR
    subgraph API["HTTP API"]
        AUTH["Auth / User / Role Controllers"]
        CAT["Location / Trip Controllers"]
        BOOK["Booking Controllers"]
        ADMIN["Admin Resource Controllers"]
    end

    subgraph BIZ["Business Services"]
        AS["Auth / Email Verification / User / Role"]
        CS["Operator / Location / Vehicle / Route / Trip"]
        BS["BookingService"]
    end

    subgraph PORTS["Outbound Ports"]
        RP["Repository Ports"]
        SP["Password / Token / OTP Ports"]
        EP["EmailVerificationSender"]
    end

    AUTH --> AS
    CAT --> CS
    BOOK --> BS
    ADMIN --> CS
    ADMIN --> BS
    AS --> RP
    AS --> SP
    AS --> EP
    CS --> RP
    BS --> RP

    DP["Data Adapters + Spring Data JPA"] -.->|implements| RP
    XP["Security Implementations"] -.->|implements| SP
    MP["SmtpEmailVerificationSender"] -.->|implements| EP
    DP --> PG[(PostgreSQL)]
    MP --> MAIL["SMTP"]
```

Sơ đồ nhóm các controller/service để dễ nhìn, **không biểu diễn đầy đủ mọi lời gọi giữa từng class**. Ví dụ một Admin Controller có thể dùng service nghiệp vụ tương ứng; không có `AdminService` tổng hợp.

## 3.5. Luồng xử lý nghiệp vụ điển hình

### Đặt vé: `POST /api/bookings`

```mermaid
sequenceDiagram
    actor Client
    participant SEC as JWT / Security Filters
    participant API as BookingController
    participant BIZ as BookingService
    participant PORT as Repository Ports
    participant ADP as Data Adapters / JPA
    participant DB as PostgreSQL

    Client->>SEC: POST /api/bookings + Bearer token
    SEC->>API: Request sau khi qua kiểm tra bảo mật
    API->>BIZ: Dữ liệu yêu cầu đã được chuyển đổi
    BIZ->>PORT: Tra cứu user / trip / booking theo use case
    PORT->>ADP: Gọi implementation
    ADP->>DB: Truy vấn và cập nhật dữ liệu
    DB-->>ADP: Kết quả
    ADP-->>PORT: Domain object / kết quả
    PORT-->>BIZ: Dữ liệu nghiệp vụ
    BIZ-->>API: Kết quả đặt vé hoặc lỗi nghiệp vụ
    API-->>Client: JSON + HTTP status
```

`BookingService.create/cancel` và `TripService.create/update/cancel` có `@Transactional`. Tạo Booking khóa Trip bằng `PESSIMISTIC_WRITE`, giảm ghế bằng update có điều kiện không âm rồi lưu Booking; lỗi lưu được rollback cùng giao dịch. Hủy Booking khóa Booking rồi Trip, kiểm tra capacity, hoàn ghế rồi đổi trạng thái. Transaction OTP và đổi role nằm tại adapter JDBC; SMTP được gọi sau khi tài khoản/challenge đã commit. Có integration test cho ghế cuối và rollback; trạng thái pass hiện tại chưa xác minh.

### Đăng ký và xác thực email

1. `AuthController` nhận yêu cầu; `AuthService` và `EmailVerificationService` xử lý nghiệp vụ tài khoản/xác minh.
2. Business sử dụng `UserRepository`, `EmailVerificationRepository` và các port mật khẩu/OTP.
3. `BCryptPasswordHasher` và `HmacVerificationCodeProvider` là các thành phần hiện thực xử lý bảo mật tương ứng.
4. `SmtpEmailVerificationSender` kết nối tới dịch vụ SMTP; **Mailpit** có thể đóng vai trò SMTP kiểm thử trong môi trường phát triển.
5. Khi đăng nhập, JWT được cấp và các request tiếp theo được kiểm tra ở tầng Security.

Cây source có `BootstrapSuperAdminService` và `RoleManagementService`; chi tiết điều kiện tạo SUPER_ADMIN, nâng/hạ quyền và quy tắc xác minh email cần tham chiếu API/kiểm thử hiện hành, thay vì suy ra chỉ từ tên class.

## 3.6. Bảo mật và xử lý lỗi

### Authentication & Authorization

- `SecurityConfig` tổ chức các thành phần Spring Security và quy tắc truy cập HTTP.
- `JwtAuthenticationFilter`, `JwtService` và `CurrentUserLoader` tham gia xử lý request có Bearer token.
- `BCryptPasswordHasher`, `HmacVerificationCodeProvider` tương ứng với mật khẩu và mã OTP.
- `security/ratelimit` gồm `AuthRateLimitFilter`, `AuthRequestRateLimiter` và cấu hình liên quan đến giới hạn request xác thực.
- Role nghiệp vụ được biểu diễn bằng `RoleName`; code hiện có các thành phần quản lý role/SUPER_ADMIN. Quyền truy cập cụ thể phải lấy từ `SecurityConfig` và service tương ứng.

```mermaid
flowchart TD
    A(["Client"])
    B["HTTP Filters / JWT"]
    C{"Xác thực và phân quyền hợp lệ?"}
    D["Controller"]
    E["Business Service"]
    F{"Đáp ứng quy tắc nghiệp vụ?"}
    G["Xử lý nghiệp vụ"]
    H["Trả HTTP Response"]
    I["401 Unauthorized / 403 Forbidden"]
    J["Trả lỗi nghiệp vụ phù hợp"]
    K(["Kết thúc"])

    A --> B --> C

    C -->|Có| D
    C -->|Không| I --> K

    D --> E --> F

    F -->|Có| G --> H --> K
    F -->|Không| J --> K
```

### Exception handling

- `domain/exception`: mô tả lỗi nghiệp vụ, không gắn trực tiếp với JSON HTTP.
- `exception/GlobalExceptionHandler`, `ErrorResponse`, `ApiErrorWriter`: hỗ trợ chuyển lỗi sang phản hồi API.
- `JsonAuthenticationEntryPoint`, `JsonAccessDeniedHandler` và `ApiExceptionBoundaryFilter`: xử lý một số trường hợp lỗi tại ranh giới Security/filter.

Việc ánh xạ lỗi sang HTTP 400/401/403/404/409 phải căn cứ vào handler thực tế và đặc tả OpenAPI; không nên cố định mã lỗi chỉ từ tên exception.

## 3.7. Kiến trúc dữ liệu và triển khai

### Database

Ứng dụng sử dụng **PostgreSQL**; phần `data` sử dụng **Spring Data JPA** cùng **JdbcTemplate** cho OTP/quản lý role; `email_verifications` không có JPA Entity riêng. `Flyway` lưu lịch sử thay đổi schema trong `src/main/resources/db/migration/` với các file V1–V16 theo cây source được cung cấp.

Tài liệu thiết kế bảng, khóa ngoại, constraint và index: [`02-database-design.md`](02-database-design.md).

### Môi trường chạy

```mermaid
flowchart LR
    C["HTTP Client"] --> APP["Spring Boot Backend"]
    APP -->|JDBC| PG[(PostgreSQL)]
    APP -->|SMTP| SMTP["SMTP server / Mailpit khi test"]
```

- **Chạy ứng dụng:** có thể thực thi Spring Boot trên máy hoặc trong container; chi tiết port, biến môi trường, health check và các service của Docker Compose phải lấy từ cấu hình triển khai thực tế.
- **Docker/Compose:** phục vụ đóng gói và khởi chạy môi trường phát triển/đánh giá. Không mặc định mọi thành phần ở sơ đồ đều có container riêng.
- **Kaggle CPU:** bài kiểm thử tải Pha 1 chạy Java, PostgreSQL và Locust trong notebook, **không phải sơ đồ triển khai Docker production**. Xem [`../load-tests/README.md`](../load-tests/README.md) để biết baseline.

