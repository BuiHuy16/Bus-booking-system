# 4. Thiết kế API


## 4.1. Quy ước chung

| Thuộc tính | Giá trị |
|---|---|
| Kiểu API | REST trên HTTP; JSON cho các request/response có body |
| Prefix | `/api` |
| URL phát triển tham khảo | `http://localhost:8085` (phụ thuộc cấu hình môi trường) |
| Xác thực | JWT: `Authorization: Bearer <token>` |
| Vai trò | `USER`, `ADMIN`, `SUPER_ADMIN` |
| Công cụ mô tả API | Springdoc OpenAPI / Swagger UI (khi được bật) |

**Nguyên tắc:** Các endpoint public không yêu cầu đăng nhập. Endpoint protected yêu cầu JWT hợp lệ và tài khoản đủ điều kiện. `ADMIN` và `SUPER_ADMIN` sử dụng các API quản trị nghiệp vụ; chỉ `SUPER_ADMIN` được quản lý vai trò. Quyền sở hữu Booking vẫn được kiểm tra tại tầng nghiệp vụ. Bearer token không hợp lệ nếu được gửi tới endpoint public có thể bị chặn theo cấu hình bảo mật hiện tại.

Các mã HTTP thường gặp: `200` (đọc/cập nhật/hủy mềm thành công), `201` (tạo), `202` (tiếp nhận yêu cầu), `204` (thành công không body), `400` (đầu vào không hợp lệ), `401` (chưa xác thực), `403` (không đủ quyền), `404` (không tìm thấy), `409` (xung đột nghiệp vụ), `429` (vượt giới hạn tần suất), `503` (dịch vụ email không khả dụng). Mã và body cụ thể phải theo controller/handler; OpenAPI hiện ghi sai `200` thay vì `201` cho POST Booking và POST Admin Location/Operator/Route/Vehicle.

## 4.2. Danh sách các API đã triển khai

| # | Method | Endpoint | Nhóm | Quyền truy cập |
|---:|---|---|---|---|
| 1 | POST | `/api/auth/register` | Authentication | Public |
| 2 | POST | `/api/auth/login` | Authentication | Public |
| 3 | GET | `/api/auth/me` | Authentication | Đăng nhập |
| 4 | POST | `/api/auth/verify-email` | Authentication | Public |
| 5 | POST | `/api/auth/resend-verification` | Authentication | Public |
| 6 | GET | `/api/users/me` | User Profile | Đăng nhập |
| 7 | PUT | `/api/users/me` | User Profile | Đăng nhập |
| 8 | GET | `/api/users/me/bookings` | Booking History | USER (lịch sử của mình); ADMIN/SUPER_ADMIN không được truy cập |
| 9 | GET | `/api/locations` | Public Location | Public |
| 10 | GET | `/api/trips` | Public Trip | Public |
| 11 | GET | `/api/trips/{tripId}` | Public Trip | Public |
| 12 | POST | `/api/bookings` | Booking | USER |
| 13 | GET | `/api/bookings/{bookingId}` | Booking | Chủ Booking / ADMIN / SUPER_ADMIN |
| 14 | DELETE | `/api/bookings/{bookingId}` | Booking | Chủ Booking / ADMIN / SUPER_ADMIN |
| 15 | PUT | `/api/admin/users/{userId}/role` | Role Management | SUPER_ADMIN |
| 16 | GET | `/api/admin/trips` | Admin Trip | ADMIN / SUPER_ADMIN |
| 17 | POST | `/api/admin/trips` | Admin Trip | ADMIN / SUPER_ADMIN |
| 18 | PUT | `/api/admin/trips/{tripId}` | Admin Trip | ADMIN / SUPER_ADMIN |
| 19 | DELETE | `/api/admin/trips/{tripId}` | Admin Trip | ADMIN / SUPER_ADMIN |
| 20 | GET | `/api/admin/bookings` | Admin Booking | ADMIN / SUPER_ADMIN |
| 21 | GET | `/api/admin/locations` | Admin Location | ADMIN / SUPER_ADMIN |
| 22 | POST | `/api/admin/locations` | Admin Location | ADMIN / SUPER_ADMIN |
| 23 | PUT | `/api/admin/locations/{locationId}` | Admin Location | ADMIN / SUPER_ADMIN |
| 24 | GET | `/api/admin/operators` | Admin Operator | ADMIN / SUPER_ADMIN |
| 25 | POST | `/api/admin/operators` | Admin Operator | ADMIN / SUPER_ADMIN |
| 26 | PUT | `/api/admin/operators/{operatorId}` | Admin Operator | ADMIN / SUPER_ADMIN |
| 27 | GET | `/api/admin/routes` | Admin Route | ADMIN / SUPER_ADMIN |
| 28 | POST | `/api/admin/routes` | Admin Route | ADMIN / SUPER_ADMIN |
| 29 | PUT | `/api/admin/routes/{routeId}` | Admin Route | ADMIN / SUPER_ADMIN |
| 30 | GET | `/api/admin/vehicles` | Admin Vehicle | ADMIN / SUPER_ADMIN |
| 31 | POST | `/api/admin/vehicles` | Admin Vehicle | ADMIN / SUPER_ADMIN |
| 32 | PUT | `/api/admin/vehicles/{vehicleId}` | Admin Vehicle | ADMIN / SUPER_ADMIN |


## 4.3. Hợp đồng và quy tắc nghiệp vụ chính

### 4.3.1. Authentication

| API | Đầu vào / ý nghĩa | Thành công |
|---|---|---|
| `POST /api/auth/register` | Bắt buộc `fullName`, `email`, `password`, `phone`; đăng ký tài khoản USER, chưa xác minh email | `201`, thông tin người dùng an toàn; **không trả JWT/OTP** |
| `POST /api/auth/login` | Bắt buộc `email`, `password`; chỉ tài khoản `ACTIVE` đã xác minh email được đăng nhập | `200`, `LoginResponse` gồm `token`, `tokenType`, `expiresIn` (giây), `user` |
| `GET /api/auth/me` | JWT trong header | `200`, `CurrentUserResponse`: `id`, `fullName`, `email`, `phone`, `role`, `emailVerified` |
| `POST /api/auth/verify-email` | Bắt buộc `email`, `code` (chuỗi 6 chữ số, có thể bắt đầu bằng `0`) | `204`, không có body |
| `POST /api/auth/resend-verification` | Bắt buộc `email` hợp lệ | `202`, thông báo chung |

**Ví dụ đăng ký:**

```http
POST /api/auth/register
Content-Type: application/json
```

```json
{
  "fullName": "Nguyen Van A",
  "email": "a@example.com",
  "password": "Password123",
  "phone": "0901234567"
}
```

**Ví dụ xác minh OTP:**

```json
{
  "email": "a@example.com",
  "code": "012345"
}
```

**Các quy tắc đã mô tả trong thiết kế triển khai:**

- Đăng ký tạo tài khoản `USER`/`ACTIVE`, `emailVerified=false`; sau xác minh mới có thể đăng nhập. Tài khoản đăng ký không được tự chọn role, trạng thái hoặc `emailVerified`. Email được trim/lowercase, tối đa 100 ký tự (phần trước `@` tối đa 64); họ tên sau trim phải có 1–100 ký tự, không chứa ký tự điều khiển; điện thoại Việt Nam được chuẩn hóa `+84` thành `0`.
- OTP dài 6 chữ số, dùng một lần, hiệu lực 10 phút, tối đa 5 lần nhập sai mỗi mã; gửi lại cách ít nhất 60 giây. OTP thô không được trả qua API và không lưu ở database; hệ thống lưu HMAC digest.
- `resend-verification` dùng phản hồi chung để hạn chế dò tìm tài khoản. OTP mới vô hiệu hóa mã trước đó.
- Mật khẩu lưu dưới dạng hash BCrypt; không trả hash trong JSON. Đăng ký yêu cầu mật khẩu không trắng, tối thiểu 8 ký tự và tối đa 72 byte UTF-8; login yêu cầu mật khẩu không rỗng, tối đa 72 byte UTF-8. JWT chỉ được cấp khi đăng nhập thành công.
- JWT được xác minh ở security filter; quyền và tình trạng tài khoản được kiểm tra theo dữ liệu hiện hành. Mặc định TTL được mô tả là 24 giờ; giá trị triển khai lấy theo cấu hình.
- Các endpoint auth còn áp dụng giới hạn tần suất theo IP/endpoint trong cấu hình hiện tại (mặc định trong tài liệu cũ: register 5, login 10, verify 20, resend 5 yêu cầu/60 giây). Đây là bộ đếm trong bộ nhớ; **không phải rate limiter dùng chung nhiều instance**.

### 4.3.2. User Profile, Booking History
| API | Ý nghĩa | Quy tắc chính |
|---|---|---|
| `GET /api/users/me` | Xem profile | `200 UserResponse` của JWT hiện tại; không nhận userId từ client |
| `PUT /api/users/me` | Sửa profile | Bắt buộc `fullName`, `phone` theo validation xác thực; trả `200 UserResponse`; không cho tự sửa email, role, password hoặc trạng thái |
| `GET /api/users/me/bookings` | Lịch sử Booking cá nhân | Chỉ USER được đọc danh sách Booking của mình; ADMIN/SUPER_ADMIN bị từ chối |

```json
{
  "fullName": "Nguyen Van B",
  "phone": "0912345678"
}
```

### 4.3.3. Public Location và Public Trip

| API | Bộ lọc / tham số | Ý nghĩa |
|---|---|---|
| `GET /api/locations` | `q` (tùy chọn) | Danh sách Location để lấy ID và tìm chuyến |
| `GET /api/trips` | `originId`, `destinationId`, `departureDate` | Tìm kiếm các chuyến xe; ngày có dạng `YYYY-MM-DD` |
| `GET /api/trips/{tripId}` | `tripId` | Chi tiết một chuyến xe |

Ví dụ:

```http
GET /api/trips?originId=1&destinationId=2&departureDate=2026-11-10
```

Dữ liệu trả về có thể chứa điểm đi/đến, thời điểm khởi hành, giá, ghế còn lại, phương tiện, nhà xe và trạng thái; **tên trường và cấu trúc lồng nhau theo `TripResponse` thực tế trong OpenAPI**. Tài liệu cũ có các ví dụ `TripResponse` không đồng nhất giữa list/detail, nên không coi các ví dụ đó là schema chính thức.

### 4.3.4. Booking

| API | Ý nghĩa | Quy tắc chính |
|---|---|---|
| `POST /api/bookings` | Tạo đặt vé | Chỉ USER; Trip tồn tại, `SCHEDULED`, quantity > 0 và đủ ghế; lưu giá tại thời điểm đặt, giảm ghế; chưa kiểm tra giờ khởi hành |
| `GET /api/bookings/{bookingId}` | Xem chi tiết | USER chỉ xem Booking của mình; ADMIN/SUPER_ADMIN có thể xem Booking khác |
| `DELETE /api/bookings/{bookingId}` | Hủy đặt vé | **Hủy nghiệp vụ**, không xóa vật lý; kiểm tra quyền, chưa `CANCELLED`, hoàn ghế không vượt capacity; chưa có cutoff theo thời gian/trạng thái Trip |

Ví dụ tạo Booking:

```http
POST /api/bookings
Content-Type: application/json
Authorization: Bearer <token>
```

```json
{
  "tripId": 5,
  "passengerName": "Nguyen Van A",
  "passengerPhone": "0901234567",
  "quantity": 2
}
```

Tạo thành công: `201 Created`, response chứa thông tin Booking (mã Booking, chuyến xe, số lượng, giá, trạng thái, thời điểm đặt). Booking đã hủy có trạng thái `CANCELLED`; các Booking hợp lệ mới được tạo có trạng thái `CONFIRMED` theo hợp đồng hiện tại.

**Tính nhất quán:** `BookingService` dùng `@Transactional`; tạo Booking khóa Trip bằng `PESSIMISTIC_WRITE`, giảm ghế bằng update có điều kiện không âm rồi lưu Booking. Hủy khóa Booking rồi Trip, kiểm tra capacity, hoàn ghế rồi đổi trạng thái. Hết ghế trả `409 INSUFFICIENT_SEATS`; Trip không `SCHEDULED`, hủy lặp hoặc không thể hoàn ghế trả `400 BUSINESS_ERROR`. Không coi mọi `409` là sự cố hạ tầng.

### 4.3.5. Role Management

`PUT /api/admin/users/{userId}/role` chỉ dành cho `SUPER_ADMIN`.

```json
{
  "role": "ADMIN"
}
```

Chỉ chấp nhận `USER` hoặc `ADMIN`. Nâng từ USER lên ADMIN yêu cầu tài khoản đủ điều kiện (ACTIVE, đã xác minh email); không hỗ trợ cấp/hạ `SUPER_ADMIN` bằng API này. Thành công trả `200 UserResponse`; mức quyền trên request tiếp theo phản ánh trạng thái role lưu trong database.

### 4.3.6. Admin Trip và Booking

| API | Tham số / dữ liệu | Quy tắc chính |
|---|---|---|
| `GET /api/admin/trips` | `routeId`, `status`, `departureDate` (tùy chọn) | Xem chuyến ở các trạng thái cho quản trị |
| `POST /api/admin/trips` | `routeId`, `vehicleId`, `departureTime`, `arrivalTime`, `price` | Tạo `SCHEDULED`, số ghế ban đầu theo capacity xe |
| `PUT /api/admin/trips/{tripId}` | Các trường Trip tương ứng | Chỉ cập nhật chuyến `SCHEDULED`; áp dụng kiểm tra đổi xe/sức chứa |
| `DELETE /api/admin/trips/{tripId}` | `tripId` | Hủy mềm Trip; không xóa DB record |
| `GET /api/admin/bookings` | `userId`, `tripId`, `status` (tùy chọn) | Xem danh sách Booking toàn hệ thống |

Ví dụ tạo/cập nhật Trip:

```json
{
  "routeId": 1,
  "vehicleId": 1,
  "departureTime": "2026-11-10T08:00:00",
  "arrivalTime": "2026-11-10T11:00:00",
  "price": 150000
}
```

- Route và Vehicle phải tồn tại và có trạng thái phù hợp. Thời điểm đến sau thời điểm đi; giá không âm và vừa giới hạn `NUMERIC(12,2)`.
- Cập nhật Trip đã có Booking phải bảo toàn Booking hiện có, giá lịch sử và số ghế hợp lệ. Nếu thay phương tiện, số ghế khả dụng cần tính theo sức chứa mới trừ số ghế của Booking `CONFIRMED`.
- Chính sách hủy Trip hiện tại: chỉ hủy chuyến `SCHEDULED` không còn Booking `CONFIRMED`; các trạng thái/xung đột khác trả `409 TRIP_CONFLICT`. Hủy lặp chuyến đã `CANCELLED` có thể trả `200` với trạng thái hiện tại.
- **Không có** API Admin xem Booking riêng theo ID hoặc sửa tùy ý trạng thái Booking: dùng `GET /api/bookings/{bookingId}` và `DELETE /api/bookings/{bookingId}` có sẵn, để không bypass nghiệp vụ hoàn ghế.
- Chưa có workflow hoàn tiền, hủy hàng loạt Booking hoặc thông báo hủy chuyến. Đây là giới hạn nghiệp vụ của Pha 1.

### 4.3.7. Admin Location

| API | Hợp đồng chính |
|---|---|
| `GET /api/admin/locations` | Tùy chọn `q`; tìm trên tên/tỉnh/địa chỉ |
| `POST /api/admin/locations` | Tạo mới với `name`, `province`, `address`; trả `201` |
| `PUT /api/admin/locations/{locationId}` | Cập nhật đầy đủ `name`, `province`, `address` |

Chuỗi được trim/gộp khoảng trắng, bộ ba `(name, province, address)` là duy nhất không phân biệt hoa/thường. Location đã có Route tham chiếu vẫn giữ nguyên ID khi sửa. Pha 1 không có DELETE Location hoặc status ACTIVE/INACTIVE cho Location.

### 4.3.8. Admin Operator

| API | Hợp đồng chính |
|---|---|
| `GET /api/admin/operators` | Tùy chọn `q`, `status` |
| `POST /api/admin/operators` | Tạo với `name`, `address`, `phone`, `email`; trạng thái mặc định `ACTIVE`; trả `201` |
| `PUT /api/admin/operators/{operatorId}` | Cập nhật đầy đủ dữ liệu và `status` |

Tên/email không được trùng theo các quy tắc chuẩn hóa; email lưu lowercase, phone được chuẩn hóa theo chính sách đầu vào. Không cho vô hiệu hóa nhà xe khi còn phương tiện/chuyến đang khai thác trái quy tắc nghiệp vụ (`409 OPERATOR_IN_USE`). Không có DELETE Operator trong Pha 1.

### 4.3.9. Admin Route

| API | Hợp đồng chính |
|---|---|
| `GET /api/admin/routes` | Tùy chọn `originId`, `destinationId`, `status` |
| `POST /api/admin/routes` | `originId`, `destinationId`, `estimatedDurationMinutes`; khởi tạo `ACTIVE`; trả `201` |
| `PUT /api/admin/routes/{routeId}` | Các trường tương ứng và `status` |

Cặp tuyến **có hướng** `(originId, destinationId)` là duy nhất; chiều ngược lại là tuyến khác. Route đã có Trip không được đổi điểm đầu/cuối; không được vô hiệu hóa khi còn Trip đang vận hành. Không có DELETE Route trong Pha 1.

### 4.3.10. Admin Vehicle

| API | Hợp đồng chính |
|---|---|
| `GET /api/admin/vehicles` | Tùy chọn `q`, `operatorId`, `status` |
| `POST /api/admin/vehicles` | `operatorId`, `licensePlate`, `vehicleType`, `seatCapacity`; khởi tạo `ACTIVE`; trả `201` |
| `PUT /api/admin/vehicles/{vehicleId}` | Các trường tương ứng và `status` |

Biển số được chuẩn hóa, là duy nhất; sức chứa phải hợp lệ. Khi Vehicle đã có Trip, không thể tùy ý đổi định danh (Operator/biển số/loại xe); việc thay sức chứa hoặc trạng thái phải kiểm tra Trip đang vận hành. Không có DELETE Vehicle trong Pha 1.

## 4.4. Phân quyền, bảo mật và xử lý lỗi

### Ma trận quyền tổng quát

| Nhóm thao tác | Public | USER | ADMIN | SUPER_ADMIN |
|---|:---:|:---:|:---:|:---:|
| Đăng ký, đăng nhập, xác minh email, gửi lại OTP | ✓ | ✓ | ✓ | ✓ |
| Xem Location, tìm/xem Trip công khai | ✓ | ✓ | ✓ | ✓ |
| Xem/cập nhật profile của chính mình | – | ✓ | ✓ | ✓ |
| Tạo Booking, xem lịch sử của chính mình | – | ✓ | – | – |
| Xem/hủy Booking của người khác | – | – | ✓ | ✓ |
| Quản trị Location, Operator, Route, Vehicle, Trip, Booking | – | – | ✓ | ✓ |
| Nâng/hạ role USER ↔ ADMIN | – | – | – | ✓ |

`USER` không được xem hoặc hủy Booking của người khác chỉ bằng cách đổi `bookingId`. `ADMIN` không được tự nâng thành `SUPER_ADMIN`. Các tình huống nhiều role/tài khoản chưa xác minh cần tuân theo `SecurityConfig` và service thực tế.

### Error response

Ứng dụng sử dụng một định dạng JSON lỗi chung (thông qua các thành phần như `ErrorResponse`, `GlobalExceptionHandler` và các security handlers). **Ví dụ minh họa mã lỗi theo tài liệu implementation; tên trường/mã cụ thể phải đối chiếu response thật:**

```json
{
  "timestamp": "2026-10-28T00:00:00Z",
  "status": 409,
  "error": "EMAIL_ALREADY_EXISTS",
  "message": "Email is already registered",
  "path": "/api/auth/register"
}
```

| HTTP | Tình huống |
|---:|---|
| 400 | Request/validation sai, enum/thời gian không hợp lệ; `BUSINESS_ERROR` khi đặt Trip không `SCHEDULED`, hủy Booking lặp hoặc không thể hoàn ghế |
| 401 | Thiếu/không hợp lệ JWT; thông tin đăng nhập không hợp lệ |
| 403 | Không đủ vai trò hoặc bị từ chối quyền sở hữu |
| 404 | Tài nguyên không tồn tại |
| 409 | Trùng định danh; `TRIP_CONFLICT` khi cập nhật/hủy Trip trái điều kiện; `INSUFFICIENT_SEATS` khi hết ghế; `ACCOUNT_NOT_ELIGIBLE` khi nâng role không đủ điều kiện |
| 429 | Vượt rate limit xác thực hoặc OTP |
| 503 | Email delivery tạm không khả dụng |

Kết quả lỗi phải được chuẩn hóa bởi exception/security boundary, không để lộ password hash, OTP, JWT hoặc thông tin nội bộ vào response. CORS chỉ cho phép origin theo cấu hình hiện tại; thông số triển khai kiểm tra tại `SecurityConfig`.

## 4.5. OpenAPI, Swagger

Swagger UI và JSON OpenAPI phụ thuộc profile/cấu hình đang bật. Bản JSON hiện tại thiếu response `200` cho login, auth/me, profile GET/PUT và đổi role, thiếu schema `LoginResponse`/`CurrentUserResponse`; các request auth/profile chưa thể hiện đầy đủ `required` và validation `@AuthField`. Có 32 operation nghiệp vụ khớp controller; 7 operation `/error` ghi `200` là đặc tả sai của error controller nội bộ, thực tế trả status lỗi từ error dispatch (mặc định 500), không tính là API nghiệp vụ. Các hạn chế này chưa được sửa trong JSON. Với backend local chạy trên cổng `8085`, địa chỉ thường dùng:

- Swagger UI: `http://localhost:8085/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8085/v3/api-docs`

