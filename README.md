# Bus Booking System

## 1. Định nghĩa bài toán

### 1.1. Bối cảnh

Hiện nay, nhu cầu di chuyển bằng xe khách giữa các tỉnh, thành phố ngày càng phổ biến. Người dùng thường phải tìm kiếm thông tin chuyến xe, thời gian khởi hành, giá vé và số ghế còn trống thông qua nhiều nguồn khác nhau. Việc đặt vé theo phương thức thủ công có thể gây mất thời gian và khó quản lý thông tin đặt vé.

Bên cạnh đó, các nhà xe cần quản lý nhiều thông tin liên quan đến phương tiện, tuyến đường, lịch trình và vé đã được đặt. Nếu quản lý bằng phương thức thủ công, việc cập nhật số lượng ghế còn trống và theo dõi các lượt đặt vé có thể dễ xảy ra sai sót.

Vì vậy, hệ thống quản lý đặt vé xe khách trực tuyến được xây dựng nhằm cung cấp một nền tảng tập trung để người dùng tìm kiếm chuyến xe và thực hiện đặt vé, đồng thời hỗ trợ quản trị viên quản lý các dữ liệu cơ bản của hệ thống.

### 1.2. Vấn đề

Hệ thống tập trung giải quyết các vấn đề chính sau:

* Người dùng khó tìm kiếm và so sánh các chuyến xe phù hợp với nhu cầu di chuyển.
* Thông tin về tuyến đường, thời gian khởi hành, giá vé và số ghế còn trống cần được quản lý tập trung.
* Việc đặt vé thủ công gây khó khăn trong việc quản lý thông tin hành khách và số lượng vé.
* Số lượng ghế còn trống cần được cập nhật sau khi có booking.
* Người dùng cần có khả năng theo dõi các booking đã thực hiện.
* Quản trị viên cần có khả năng quản lý dữ liệu của hệ thống.

## 2. Phân chia công việc

| Thành viên           | Công việc                                                           |
| -------------------- | ------------------------------------------------------------------- |
| Nguyễn Văn Huy Hoàng | Auth, Role, User, Operator, Vehicle, Location, Route, Security |
| Bùi Công Huy         | Trip, Booking, Exception Handling, Test, Swagger/OpenAPI, Docker, Test Kaggle CPU  |

## 3. Công nghệ sử dụng

* Java 21, Spring Boot
* Spring Security, JWT
* PostgreSQL, Spring Data JPA
* Swagger/OpenAPI
* JUnit, Mockito
* Docker, Docker Compose

## 4. Kiến trúc hệ thống

Áp dụng kiến trúc phân lớp:

`API → Business → Data Access → PostgreSQL`

Các lớp được phân chia trách nhiệm rõ ràng, hỗ trợ bảo trì và kiểm thử độc lập.

## 5. Tài liệu project

Tài liệu đặc tả chi tiết được lưu tại thư mục [`docs/`](docs/).

- [`01-overview-and-requirements.md`](docs/01-overview-and-requirements.md)
- [`02-database-design.md`](docs/02-database-design.md)
- [`03-system-archiecture.md`](docs/03-system-archiecture.md)
- [`04-api-design.md`](docs/04-api-design.md)
- [`05-source-code-design.md`](docs/05-source-code-design.md)
- [`06-quality-attributes.md`](docs/06-quality-attributes.md)
- [Auth — Chức năng, API và hướng dẫn sử dụng](docs/07-auth-guide.md)

## 6. Auth đã hoàn thiện

Phụ trách: **Nguyễn Văn Huy Hoàng**, nhánh `feature/hoang`.

- Đăng ký USER, xác minh OTP email, gửi lại mã và đăng nhập JWT.
- Đọc/cập nhật profile; số điện thoại Việt Nam chỉ dùng liên hệ.
- Ba role USER/ADMIN/SUPER_ADMIN; chỉ một super admin và chỉ super được nâng/hạ USER ↔ ADMIN.
- Security stateless, JSON lỗi thống nhất, rate limit, CORS và Swagger bearerAuth.

Tài liệu duy nhất cho phần Auth: [07-auth-guide.md](docs/07-auth-guide.md). Tài liệu này thay thế docs 07–19 cũ, có contract API, cấu hình môi trường, bootstrap, demo, lệnh kiểm thử và phần bàn giao cho Huy.

Yêu cầu JDK 21 đầy đủ, PostgreSQL test riêng và Mailpit khi kiểm thử SMTP thật. Ứng dụng mặc định cổng **8085**; Swagger tại http://localhost:8085/swagger-ui/index.html. `.env.example` chỉ là mẫu, Spring Boot không tự nạp `.env`.

Sau khi cấu hình biến môi trường theo tài liệu:

```bash
bash mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Kiểm thử mặc định và smoke opt-in:

```bash
bash mvnw -B clean verify
# Cần thêm Mailpit; chạy tuần tự với các suite dùng cùng database:
bash mvnw -B '-Dtest=*Test,*Tests,*IT' clean verify
```

Nghiệm thu Auth trước đây: **151 test pass sau sửa review (148 test mặc định + 3 smoke opt-in)**. Sau khi tích hợp Trip/Booking/Admin Management, Locations và BusOperators, default suite trước G3 có **249 test**. G3–G4 bổ sung Transport Routes, Vehicles API và migrations V14–V15. Các request mẫu ở [requests/](requests/).

## 7. Locations đã hoàn thiện

- Public discovery: `GET /api/locations`, hỗ trợ tìm kiếm `q`.
- Admin management: `GET`, `POST`, `PUT /api/admin/locations`.
- Chuỗi được trim/gộp whitespace; bộ ba tên, tỉnh/thành, địa chỉ là duy nhất không phân biệt hoa/thường.
- Không xóa vật lý và không thêm trạng thái Location trong Phase 1.
- Migration V12 và request mẫu nằm tại `src/main/resources/db/migration/` và `requests/locations.http`.

## 8. BusOperators đã hoàn thiện

- Admin management: `GET`, `POST`, `PUT /api/admin/operators`, hỗ trợ filter `q` và `status`.
- Name/email unique; email, phone và whitespace được chuẩn hóa.
- POST mặc định ACTIVE; không cho chuyển INACTIVE khi còn Vehicle/Trip vận hành và không cascade dữ liệu.
- Migration V13 và request mẫu nằm tại `src/main/resources/db/migration/` và `requests/operators.http`.

## 9. Transport Routes đã hoàn thiện

- Admin management: `GET`, `POST`, `PUT /api/admin/routes`; hỗ trợ filter `originId`, `destinationId`, `status`.
- Cặp điểm đi/đến có hướng là duy nhất; POST mặc định `ACTIVE`.
- Không đổi hai đầu Route đã có Trip; không ngừng Route còn Trip `SCHEDULED`/`IN_PROGRESS`.
- Migration V14 và request mẫu nằm tại `src/main/resources/db/migration/` và `requests/routes.http`.

## 10. Vehicles đã hoàn thiện

- Admin management: `GET`, `POST`, `PUT /api/admin/vehicles`; hỗ trợ `q`, `operatorId`, `status`.
- Biển số uppercase/xóa whitespace và unique; POST mặc định `ACTIVE`.
- Khóa thông tin nhận diện sau khi có Trip; bảo vệ capacity/status khi còn Trip vận hành.
- Migration V15 và request mẫu nằm tại `src/main/resources/db/migration/` và `requests/vehicles.http`.

Kiểm thử riêng toàn bộ bốn module dữ liệu nền tảng và workflow liên kết:

```bash
bash mvnw -B '-Dtest=LocationManagementIntegrationTest,OperatorManagementIntegrationTest,RouteManagementIntegrationTest,VehicleManagementIntegrationTest,CoreCatalogWorkflowIntegrationTest' test
```
