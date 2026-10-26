# 4. Thiết kế API

**Trạng thái tài liệu (cập nhật 24/10/2026):** Các API Authentication (5), User Profile (2), Trip công khai (2), Location công khai (1), Booking (4), quản lý role (1), Admin Trip/Booking (5), Admin Location (3), Admin Operator (3), Admin Route (3) và Admin Vehicle (3) đã triển khai. API User Status trong mục 4.6 vẫn là **thiết kế dự kiến**. Kế hoạch API Admin đã tinh gọn từ 25 xuống 19 endpoint: bỏ 2 endpoint trùng nghiệp vụ Booking, hoãn 4 endpoint DELETE dữ liệu nền tảng. **Tổng phạm vi hiện tại: 34 API**, gồm 32 endpoint đã triển khai và 2 endpoint Admin dự kiến. Mục 4.9–4.10 là ghi chép lịch sử theo phase, không phản ánh trạng thái triển khai hiện tại. Base URL local: `http://localhost:8085/api`.

## 4.1. API Conventions

### 4.1.1. Base URL

```text
/api
```

Các endpoint được tổ chức theo resource:

```text
/api/auth
/api/users
/api/trips
/api/bookings
/api/admin
```

### 4.1.2. Request/Response Format

API sử dụng **JSON** cho request và response.

Request:

```http
Content-Type: application/json
```

Response thành công:

```http
Content-Type: application/json
```

### 4.1.3. Authentication Header

Các API yêu cầu đăng nhập sử dụng:

```http
Authorization: Bearer <JWT_TOKEN>
```

Các API công khai như đăng ký, xác minh email qua OTP, yêu cầu gửi lại mã OTP, đăng nhập và tìm kiếm chuyến xe không bắt buộc token. Đối với các endpoint auth công khai, Bearer token không hợp lệ nếu được gửi kèm vẫn bị từ chối theo quy tắc Security hiện tại.

---

## 4.2. Authentication APIs

### 4.2.1. Register

```http
POST /api/auth/register
```

Đăng ký USER/ACTIVE/emailVerified=false và gửi OTP đến email đã đăng ký; không cấp JWT. Người dùng phải xác minh email qua `POST /api/auth/verify-email` trước khi đăng nhập; nếu cần, có thể yêu cầu gửi lại OTP qua `POST /api/auth/resend-verification` (xem mục 4.2.4–4.2.5 và contract 4.11).

Request:

```json
{
  "fullName": "Nguyen Van A",
  "email": "a@example.com",
  "password": "Password123",
  "phone": "0901234567"
}
```

Response `201 Created`:

```json
{
  "id": 1,
  "fullName": "Nguyen Van A",
  "email": "a@example.com",
  "phone": "0901234567",
  "role": "USER",
  "emailVerified": false
}
```

### 4.2.2. Login

```http
POST /api/auth/login
```

Đăng nhập tài khoản ACTIVE đã xác minh OTP email và nhận JWT.

Request:

```json
{
  "email": "a@example.com",
  "password": "Password123"
}
```

Response `200 OK`:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": 1,
    "fullName": "Nguyen Van A",
    "email": "a@example.com",
    "phone": "0901234567",
    "role": "USER",
    "emailVerified": true
  }
}
```

### 4.2.3. Get Current User

```http
GET /api/auth/me
```

**Authentication:** Required

Lấy thông tin tài khoản đang đăng nhập.

Response:

```json
{
  "id": 1,
  "fullName": "Nguyen Van A",
  "email": "a@example.com",
  "phone": "0901234567",
  "role": "USER",
  "emailVerified": true
}
```

### 4.2.4. Verify Email (Xác minh email bằng OTP)

```http
POST /api/auth/verify-email
```

**Authentication:** Not required (Public).

Xác minh địa chỉ email sau khi đăng ký bằng mã OTP gồm **6 chữ số**, được gửi qua SMTP. Giá trị `code` phải là **chuỗi**, không phải số nguyên, để giữ số `0` ở đầu nếu có.

Request:

```json
{
  "email": "a@example.com",
  "code": "012345"
}
```

Response thành công: **`204 No Content`**, không có response body và **không tự cấp JWT**. Sau khi xác minh thành công, `users.email_verified` chuyển thành `true`; challenge OTP đã dùng bị xóa, người dùng có thể đăng nhập bằng API Login ở mục 4.2.2.

Quy tắc nghiệp vụ:

- OTP có hiệu lực **10 phút**, chỉ dùng **một lần**.

- Tối đa **5 lần nhập sai cho mỗi mã**.

- Mã sai, hết hạn, đã dùng hoặc vượt giới hạn lần thử được xử lý thống nhất bằng `400 INVALID_VERIFICATION_CODE`.

- Hệ thống không trả OTP hoặc hash OTP trong response; database chỉ lưu HMAC digest của OTP.

### 4.2.5. Resend Email Verification (Gửi lại mã OTP)

```http
POST /api/auth/resend-verification
```

**Authentication:** Not required (Public).

Gửi lại mã xác minh tới email đăng ký khi người dùng chưa nhận được OTP, OTP hết hạn hoặc thao tác đăng ký đã hoàn tất nhưng gửi email ban đầu thất bại.

Request:

```json
{
  "email": "a@example.com"
}
```

Response thành công: **`202 Accepted`**, trả thông báo chung để hạn chế tiết lộ email có tồn tại trong hệ thống hay không. Tài khoản không tồn tại, đã xác minh hoặc `INACTIVE` không được gửi OTP mới.

Quy tắc nghiệp vụ:

- Mỗi tài khoản phải chờ ít nhất **60 giây** giữa các lần gửi mã hợp lệ.

- Gửi lại thành công tạo mã mới, vô hiệu hóa mã cũ, đặt lại thời hạn và số lần nhập sai.

- Request bị giới hạn tần suất có thể trả `429 RATE_LIMITED`; khi dịch vụ gửi email không khả dụng có thể trả `503 EMAIL_DELIVERY_UNAVAILABLE`.

- OTP chỉ được gửi qua SMTP, không trả trong JSON response.

---

## 4.3. User APIs

### 4.3.1. Get User Profile

```http
GET /api/users/me
```

**Authentication:** Required

Lấy thông tin profile của người dùng hiện tại.

### 4.3.2. Update User Profile

```http
PUT /api/users/me
```

**Authentication:** Required

Request:

```json
{
  "fullName": "Nguyen Van B",
  "phone": "0912345678"
}
```

Response:

```json
{
  "id": 1,
  "fullName": "Nguyen Van B",
  "email": "a@example.com",
  "phone": "0912345678",
  "role": "USER",
  "emailVerified": true
}
```

### 4.3.3. Get Booking History

```http
GET /api/users/me/bookings
```

**Authentication:** Required

Lấy danh sách các booking của người dùng hiện tại.

Response:

```json
[
  {
    "id": 10,
    "bookingCode": "BK-819207a570db4323a216",
    "tripId": 5,
    "passengerName": "Nguyen Van A",
    "passengerPhone": "0901234567",
    "quantity": 2,
    "totalAmount": 300000,
    "status": "CONFIRMED",
    "bookedAt": "2026-10-09T10:30:00"
  }
]
```

---

## 4.4. Trip APIs

### 4.4.1. Search Trips

```http
GET /api/trips
```

**Authentication:** Not required

Tìm kiếm các chuyến xe theo điểm đi, điểm đến và ngày khởi hành.

Query parameters:

```text
originId
destinationId
departureDate
```

Example:

```http
GET /api/trips?originId=1&destinationId=2&departureDate=2026-10-15
```

Response:

```json
[
  {
    "id": 5,
    "origin": "Ha Noi",
    "destination": "Da Nang",
    "departureTime": "2026-10-15T08:00:00",
    "arrivalTime": "2026-10-15T20:00:00",
    "price": 250000,
    "availableSeats": 20,
    "vehicleType": "BUS",
    "operatorName": "ABC Bus",
    "status": "SCHEDULED"
  }
]
```

### 4.4.2. Get Trip Detail

```http
GET /api/trips/{tripId}
```

**Authentication:** Not required

Lấy thông tin chi tiết của một chuyến xe.

Example:

```http
GET /api/trips/5
```

Response:

```json
{
  "id": 5,
  "route": {
    "origin": "Ha Noi",
    "destination": "Da Nang",
    "estimatedDurationMinutes": 720
  },
  "vehicle": {
    "vehicleType": "BUS",
    "seatCapacity": 40
  },
  "departureTime": "2026-10-15T08:00:00",
  "arrivalTime": "2026-10-15T20:00:00",
  "price": 250000,
  "availableSeats": 20,
  "status": "SCHEDULED"
}
```

---

## 4.5. Booking APIs

### 4.5.1. Create Booking

```http
POST /api/bookings
```

**Authentication:** Required

Tạo booking cho chuyến xe.

Request:

```json
{
  "tripId": 5,
  "passengerName": "Nguyen Van A",
  "passengerPhone": "0901234567",
  "quantity": 2
}
```

Response `201 Created`:

```json
{
  "id": 10,
  "bookingCode": "BK-819207a570db4323a216",
  "tripId": 5,
  "passengerName": "Nguyen Van A",
  "passengerPhone": "0901234567",
  "quantity": 2,
  "unitPrice": 250000,
  "totalAmount": 500000,
  "status": "CONFIRMED",
  "bookedAt": "2026-10-09T10:30:00"
}
```

### 4.5.2. Get Booking Detail

```http
GET /api/bookings/{bookingId}
```

**Authentication:** Required

Người dùng chỉ được xem booking của chính mình. Admin có thể xem booking của mọi người dùng.

### 4.5.3. Cancel Booking

```http
DELETE /api/bookings/{bookingId}
```

**Authentication:** Required

Hủy booking.

Khi booking được hủy, số ghế khả dụng của chuyến xe được cập nhật lại theo business rule.

Response:

```json
{
  "id": 10,
  "bookingCode": "BK-819207a570db4323a216",
  "status": "CANCELLED"
}
```

---

## 4.6. Admin APIs

Các API quản trị yêu cầu JWT hợp lệ và phân quyền:

```text
Role: ADMIN/SUPER_ADMIN (riêng quản lý role chỉ SUPER_ADMIN)
```

**Phạm vi đề xuất cho Phase 1:** 19 endpoint Admin nghiệp vụ, so với 25 endpoint ở thiết kế ban đầu. Không triển khai xóa vật lý đối với dữ liệu nền tảng đã được liên kết. Năm endpoint Admin Trip/Booking, ba endpoint Admin Location và ba endpoint Admin Operator đã triển khai; 8 endpoint còn lại vẫn là thiết kế. Riêng `PUT /api/admin/users/{userId}/role` được mô tả ở mục 4.12 và đã triển khai.

### 4.6.1. User Management

Lấy danh sách người dùng:

```http
GET /api/admin/users
```

Chỉ trả thông tin cần thiết cho quản trị; không trả password hash, OTP hoặc JWT.

Cập nhật trạng thái người dùng:

```http
PUT /api/admin/users/{userId}/status
```

Request:

```json
{
  "status": "INACTIVE"
}
```

Trạng thái hợp lệ theo schema hiện tại: `ACTIVE` hoặc `INACTIVE`. Cần áp dụng các ràng buộc về quyền hạn và việc vô hiệu hóa tài khoản theo business rule; không sử dụng API này để cấp quyền `SUPER_ADMIN`.

### 4.6.2. Bus Operator Management

Bus Operator Management đã triển khai. Name và email là duy nhất không phân biệt hoa/thường; email lưu lowercase, phone Việt Nam được chuẩn hóa về đầu `0`, name/address được trim và gộp whitespace. POST luôn tạo `ACTIVE`; PUT thay thế đầy đủ dữ liệu.

Lấy danh sách nhà xe:

```http
GET /api/admin/operators
```

Hỗ trợ `q` tùy chọn để tìm substring trong name/address/phone/email và `status=ACTIVE|INACTIVE`. `q` sau chuẩn hóa dài tối đa 100 ký tự. Kết quả không phân trang, sắp xếp theo name rồi id.

Tạo nhà xe:

```http
POST /api/admin/operators
```

Request gồm `name`, `address`, `phone`, `email`; không chấp nhận `status`. Thành công trả `201`, `OperatorResponse` và header `Location: /api/admin/operators/{id}`.

Cập nhật nhà xe:

```http
PUT /api/admin/operators/{operatorId}
```

PUT bắt buộc đủ `name`, `address`, `phone`, `email`, `status`. Chuyển sang `INACTIVE` trả `409 OPERATOR_IN_USE` nếu còn Vehicle ACTIVE/MAINTENANCE hoặc Trip SCHEDULED/IN_PROGRESS. Không cascade trạng thái.

**Thay đổi so với thiết kế ban đầu:** Hoãn `DELETE /api/admin/operators/{operatorId}`. Đối với nhà xe không còn hoạt động, ưu tiên cập nhật `status = INACTIVE` bằng API PUT, thay vì xóa bản ghi có phương tiện liên kết.

### 4.6.3. Vehicle Management

Vehicle Management đã triển khai. Biển số được uppercase/xóa whitespace và unique; `vehicleType` được trim/gộp whitespace. POST luôn tạo `ACTIVE`. Operator phải `ACTIVE` khi tạo, chuyển nhà xe hoặc đưa Vehicle sang `ACTIVE/MAINTENANCE`.

Lấy danh sách xe:

```http
GET /api/admin/vehicles?q={optional}&operatorId={optional}&status={optional}
```

Tạo xe:

```http
POST /api/admin/vehicles
```

Request gồm `operatorId`, `licensePlate`, `vehicleType`, `seatCapacity`; không nhận `status`. Thành công trả `201`, VehicleResponse chứa Operator summary và header `Location: /api/admin/vehicles/{id}`. Biển số trùng trả `409 VEHICLE_ALREADY_EXISTS`.

Cập nhật xe:

```http
PUT /api/admin/vehicles/{vehicleId}
```

PUT bắt buộc đủ bốn trường trên và `status`. Khi Vehicle đã có Trip, không được đổi Operator, biển số hoặc loại xe (`409 VEHICLE_IDENTITY_LOCKED`). Thay đổi capacity hoặc chuyển từ ACTIVE sang INACTIVE/MAINTENANCE bị chặn nếu còn Trip SCHEDULED/IN_PROGRESS (`409 VEHICLE_IN_USE`).

**Thay đổi so với thiết kế ban đầu:** Hoãn `DELETE /api/admin/vehicles/{vehicleId}`. Ưu tiên quản lý `status` (`ACTIVE`, `INACTIVE`, `MAINTENANCE`) và không làm mất quan hệ với Trip hoặc Booking lịch sử. Cần kiểm tra các Trip đang hoạt động trước khi thay đổi xe hoặc sức chứa.

### 4.6.4. Location Management

Location Management đã triển khai. Chuỗi đầu vào được trim và gộp khoảng trắng; bộ ba `name`, `province`, `address` là duy nhất không phân biệt hoa/thường. Location đã được Route tham chiếu vẫn được phép cập nhật và giữ nguyên ID. Không có DELETE hoặc status trong Phase 1.

Lấy danh sách địa điểm:

```http
GET /api/admin/locations
```

Hỗ trợ query parameter `q` tùy chọn, tìm substring không phân biệt hoa/thường trên tên, tỉnh/thành và địa chỉ. `q` sau chuẩn hóa dài tối đa 100 ký tự. Kết quả không phân trang, sắp xếp theo `province`, `name`, `id`.

Tạo địa điểm:

```http
POST /api/admin/locations
```

Request bắt buộc đủ `name`, `province`, `address`. Thành công trả `201`, `LocationResponse` và header `Location: /api/admin/locations/{id}`. Bản ghi trùng trả `409 LOCATION_ALREADY_EXISTS`.

Cập nhật địa điểm:

```http
PUT /api/admin/locations/{locationId}
```

PUT thay thế đầy đủ ba trường và trả `200`. Location không tồn tại trả `404`.

**Thay đổi so với thiết kế ban đầu:** Hoãn `DELETE /api/admin/locations/{locationId}` vì Location có thể đang được các Route tham chiếu. Bảng `locations` hiện chưa có cột `status`; không giả định có thể vô hiệu hóa Location bằng `ACTIVE/INACTIVE` nếu chưa bổ sung thiết kế/schema.

API discovery công khai phục vụ client lấy ID cho Trip search:

```http
GET /api/locations?q={optional}
```

Endpoint này không yêu cầu JWT, dùng cùng filter, sort và `LocationResponse` đầy đủ như danh sách Admin.

### 4.6.5. Route Management

Route Management đã triển khai. Cặp có hướng `origin → destination` là duy nhất; chiều ngược lại được phép. POST luôn tạo `ACTIVE`. Route đã có Trip không được đổi hai đầu; chuyển sang `INACTIVE` bị chặn nếu còn Trip `SCHEDULED` hoặc `IN_PROGRESS`.

Lấy danh sách tuyến:

```http
GET /api/admin/routes?originId={optional}&destinationId={optional}&status={optional}
```

Tạo tuyến:

```http
POST /api/admin/routes
```

Request bắt buộc `originId`, `destinationId`, `estimatedDurationMinutes`; không nhận `status`. Thành công trả `201`, RouteResponse chứa đầy đủ hai Location và header `Location: /api/admin/routes/{id}`. Cặp trùng trả `409 ROUTE_ALREADY_EXISTS`.

Cập nhật tuyến:

```http
PUT /api/admin/routes/{routeId}
```

PUT thay thế đầy đủ `originId`, `destinationId`, `estimatedDurationMinutes`, `status`. Đổi hai đầu Route đã có Trip trả `409 ROUTE_ENDPOINTS_LOCKED`; ngừng Route còn Trip vận hành trả `409 ROUTE_IN_USE`.

**Thay đổi so với thiết kế ban đầu:** Hoãn `DELETE /api/admin/routes/{routeId}`. Ưu tiên chuyển Route sang `INACTIVE` qua API PUT khi không còn khai thác; không xóa lịch sử chuyến xe sử dụng tuyến đó.

### 4.6.6. Trip Management

Lấy danh sách chuyến phục vụ quản trị:

```http
GET /api/admin/trips
```

Đã triển khai với bộ lọc riêng `routeId`, `status`, `departureDate`, xem mọi trạng thái Trip. API công khai `GET /api/trips` giữ nguyên contract và hành vi hiện tại.

Tạo chuyến:

```http
POST /api/admin/trips
```

Cập nhật chuyến:

```http
PUT /api/admin/trips/{tripId}
```

Hủy chuyến (không xóa vật lý):

```http
DELETE /api/admin/trips/{tripId}
```

**Đã triển khai Phase 1 (24/10/2026):** Endpoint DELETE chuyển `trips.status` sang `CANCELLED`, không xóa bản ghi. Chính sách tạm thời: chỉ hủy Trip `SCHEDULED` không có Booking `CONFIRMED`; nếu có thì trả `409 TRIP_CONFLICT`. Hủy lặp trả `200` với Trip đã hủy. Không tự động hủy Booking hay hoàn tiền. Chính sách cuối cùng về hoàn tiền/hủy hàng loạt cần được nghiệp vụ phê duyệt. Chi tiết contract của cả 5 API Admin Trip/Booking ở mục 4.13.

### 4.6.7. Booking Management

Lấy danh sách booking toàn hệ thống:

```http
GET /api/admin/bookings
```

Đã triển khai: xem Booking của mọi tài khoản, lọc tùy chọn theo `userId`, `tripId`, `status`. Response dùng `BookingResponse` hiện có. Phân trang chưa thuộc Phase 1.

**Thay đổi so với thiết kế ban đầu:**

- **Bỏ** `GET /api/admin/bookings/{bookingId}`: dùng `GET /api/bookings/{bookingId}` đã cho phép Admin xem chi tiết Booking theo quy tắc phân quyền.
- **Bỏ** `PUT /api/admin/bookings/{bookingId}/status`: không cho cập nhật tùy ý `status` mà bỏ qua nghiệp vụ. Admin hủy Booking bằng `DELETE /api/bookings/{bookingId}`, đi qua `BookingService.cancel()` để cập nhật `CANCELLED` và hoàn ghế trong cùng transaction.

Các API Booking hiện có vẫn phải kiểm tra ownership đối với USER và quyền Admin/SUPER_ADMIN theo SecurityConfig; không được cho USER xem/hủy Booking của người khác.

---

## 4.7. Error Response

API sử dụng một format thống nhất cho các lỗi.

### 4.7.1. Error Format

```json
{
  "timestamp": "2026-10-09T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Quantity must be greater than 0",
  "path": "/api/bookings"
}
```

### 4.7.2. Validation Error

Khi request chứa dữ liệu không hợp lệ:

```json
{
  "timestamp": "2026-10-09T10:30:00",
  "status": 400,
  "error": "Validation Error",
  "message": "Request validation failed",
  "path": "/api/bookings",
  "details": {
    "quantity": "must be greater than 0"
  }
}
```

### 4.7.3. Authentication Error

Khi chưa đăng nhập hoặc JWT không hợp lệ:

```json
{
  "timestamp": "2026-10-09T10:30:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication is required",
  "path": "/api/bookings"
}
```

### 4.7.4. Authorization Error

Khi người dùng đã đăng nhập nhưng không có quyền:

```json
{
  "timestamp": "2026-10-09T10:30:00",
  "status": 403,
  "error": "Forbidden",
  "message": "You do not have permission to access this resource",
  "path": "/api/admin/users"
}
```

### 4.7.5. Resource Not Found

```json
{
  "timestamp": "2026-10-09T10:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Trip not found",
  "path": "/api/trips/999"
}
```

### 4.7.6. Business Conflict

Ví dụ khi số ghế không đủ:

```json
{
  "timestamp": "2026-10-09T10:30:00",
  "status": 409,
  "error": "Conflict",
  "message": "Not enough available seats",
  "path": "/api/bookings"
}
```

---

## 4.8. Authentication & Authorization

### 4.8.1. Authentication Flow

Trước lần đăng nhập đầu tiên, người dùng đăng ký tài khoản và thực hiện bước xác thực email bằng OTP:

```text
POST /api/auth/register
-> Nhận OTP qua email
-> POST /api/auth/verify-email
-> users.email_verified = true
-> POST /api/auth/login
```

Nếu chưa nhận được mã, người dùng gọi `POST /api/auth/resend-verification` rồi xác minh bằng mã mới. OTP không thay thế JWT: chỉ sau khi đăng nhập thành công, client mới nhận JWT.

Luồng đăng nhập và cấp JWT:

```text
Client
│
│ POST /api/auth/login
▼
AuthController
│
▼
AuthService
│
│ Verify email/password
▼
UserRepository
│
▼
PostgreSQL
│
│ User information
▼
AuthService
│
│ Generate JWT
▼
Client
```

Sau khi đăng nhập thành công, client nhận JWT và gửi token trong các request cần xác thực:

```http
Authorization: Bearer <JWT_TOKEN>
```

### 4.8.2. Request Authentication Flow

```text
Client
│
│ HTTP Request + JWT
▼
Security Filter
│
├── Invalid/Missing Token ──► 401 Unauthorized
│
▼
Authentication Context
│
├── Insufficient Role ───────► 403 Forbidden
│
▼
Controller
│
▼
Business Service
│
▼
Response
```

### 4.8.3. Role-Based Authorization

Hệ thống có ba role:

| Role  | Quyền                                                                                 |
| ----- | ------------------------------------------------------------------------------------- |
| USER  | Tìm chuyến xe, xem chuyến xe, tạo booking, xem booking của mình, hủy booking của mình |
| ADMIN | Các route quản trị nghiệp vụ; không quản lý role |
| SUPER_ADMIN | Có quyền quản trị ADMIN và chỉ role này nâng/hạ USER ↔ ADMIN |

Các route quản trị nghiệp vụ dành cho ADMIN/SUPER_ADMIN; riêng `PUT /api/admin/users/{userId}/role` chỉ SUPER_ADMIN. Các endpoint CRUD quản trị ở mục 4.6 là thiết kế dự kiến, chưa xác nhận đã triển khai.

Ví dụ:

```text
GET /api/admin/users
POST /api/admin/operators
POST /api/admin/vehicles
POST /api/admin/routes
POST /api/admin/trips
```

đều yêu cầu:

```text
ROLE_ADMIN hoặc ROLE_SUPER_ADMIN (riêng quản lý role chỉ ROLE_SUPER_ADMIN)
```

### 4.8.4. Resource Ownership

Role-based authorization không thay thế kiểm tra quyền sở hữu tài nguyên.

Ví dụ:

```text
USER A
  └── GET /api/bookings/10
       └── Booking 10 thuộc USER A → Allow

USER B
  └── GET /api/bookings/10
       └── Booking 10 thuộc USER A → Deny
```

Việc kiểm tra ownership được thực hiện trong **Business Layer**, vì đây là business rule của hệ thống.

### 4.8.5. Public and Protected APIs

| API                          | Authentication | Role       |
| ---------------------------- | -------------- | ---------- |
| `POST /api/auth/register`    | Không          | Public     |
| `POST /api/auth/verify-email` | Không | Public |
| `POST /api/auth/resend-verification` | Không | Public |
| `POST /api/auth/login`       | Không          | Public     |
| `GET /api/trips`             | Không          | Public     |
| `GET /api/trips/{id}`        | Không          | Public     |
| `GET /api/auth/me`           | Có             | USER/ADMIN/SUPER_ADMIN |
| `GET /api/users/me`          | Có             | USER/ADMIN/SUPER_ADMIN |
| `GET /api/users/me/bookings` | Có             | USER       |
| `POST /api/bookings`         | Có             | USER       |
| `GET /api/bookings/{id}`     | Có             | USER/ADMIN/SUPER_ADMIN |
| `DELETE /api/bookings/{id}`  | Có             | USER/ADMIN/SUPER_ADMIN |
| `/api/admin/**`              | Có             | ADMIN/SUPER_ADMIN |

### 4.8.6. Security Principle

Authentication và authorization được xử lý bởi SecurityConfig/JwtAuthenticationFilter; ownership thuộc business.

## 4.9. Hợp đồng auth cập nhật sau phase 1

**Lịch sử phase 1:** các quy tắc dưới đây đã có ở service/data/SMTP và được kiểm thử; AuthController, JWT thật và SecurityConfig chưa triển khai. Không coi các endpoint trong tài liệu là đã hoạt động ở phase 1.

- Email dùng để đăng nhập; strip/lowercase Locale.ROOT, tối đa 100 ký tự, định dạng ASCII email có domain; không OTP qua số điện thoại.

- Password đăng ký tối thiểu 8 ký tự Unicode, tối đa 72 byte UTF-8, không toàn khoảng trắng; không trim/lowercase. Login không áp lại giới hạn tối thiểu đăng ký. BCrypt cost 10.

- Phone nhận dạng nội địa: mobile 10 số bắt đầu 03/05/07/08/09 hoặc cố định 11 số bắt đầu 02; chấp nhận +84 thay 0 đầu và chuẩn hóa về 0; không nhận dấu phân cách bên trong. Đây là kiểm tra định dạng, không chứng minh quyền sở hữu số.

- Register không nhận id/role/status/emailVerified; tạo USER/ACTIVE với emailVerified=false, gửi OTP qua SMTP sau commit. Response dự kiến thêm emailVerified=false vào thông tin user, không trả OTP/hash/token.

- Login chỉ cho phép ACTIVE và emailVerified=true; email không tồn tại, sai password, INACTIVE hoặc chưa xác minh đều dùng thông báo chung Invalid email or password.

- OTP 6 chữ số, có hiệu lực 10 phút, chỉ dùng một lần, tối đa 5 lần nhập sai cho mỗi mã. Gửi lại cách ít nhất 60 giây, thay mã cũ và reset attempts. Tài khoản không tồn tại/đã xác minh/INACTIVE không nhận email gửi lại.

Endpoint sẽ bổ sung ở phase 3:

| Endpoint dự kiến | Request | Thành công |
| --- | --- | --- |
| POST /api/auth/verify-email | email, code (chuỗi 6 số, giữ số 0 đầu) | 204, không tự cấp JWT |
| POST /api/auth/resend-verification | email | 202 với thông báo chung |

Mapping lỗi dự kiến: InvalidAuthInputException → 400; DuplicateEmailException → 409; InvalidCredentialsException → 401; InvalidVerificationCodeException → 400 (mã sai/hết hạn/đã dùng/vượt attempts dùng cùng thông báo); VerificationRateLimitException → 429; EmailDeliveryException → 503; thiếu seed/lỗi DB/provider → 500.

Nếu SMTP lỗi sau register, user/challenge vẫn tồn tại và emailVerified=false. Client hướng dẫn gửi lại sau cooldown; không tự đăng ký lại để tránh trùng email. Endpoint gửi lại cần thêm rate limiting theo nguồn request ở tầng HTTP khi triển khai API; giới hạn theo tài khoản đã có ở database. Khi tích hợp với Huy, handler BusinessException chung phải nhường ưu tiên cho các handler auth cụ thể để không biến 401/409/429/503 thành 400.

## 4.10. Cập nhật sau phase 2 — JWT service và filter

AuthService đã được wiring với JwtService thật. Login ở tầng service cấp signed JWT HS256 với sub là user ID dạng chuỗi, iat/exp/iss; expiresIn là giây. JWT không chứa email/quyền/trạng thái/hash; CurrentUserLoader lấy lại dữ liệu từ database và kiểm tra ACTIVE + emailVerified. Khóa/xóa hoặc thu hồi xác minh làm request với token cũ bị từ chối; đổi role áp dụng ở request tiếp theo.

JWT_SECRET là key Base64 tối thiểu 32 byte giải mã, tách key OTP; JWT_ISSUER phải khớp; TTL mặc định 24 giờ và không nới expiration. exp bằng hiện tại là hết hạn; iat tương lai, exp không sau iat, sub không hợp lệ hoặc thiếu claim bắt buộc bị từ chối. Giới hạn chuỗi token tối đa 8192 ký tự.

JwtAuthenticationFilter chấp nhận scheme Bearer không phân biệt hoa thường với một khoảng trắng, một token không có whitespace; thiếu Authorization hoặc scheme khác đi tiếp không tạo danh tính. Authorization trùng, Bearer rỗng/hỏng/hết hạn hoặc tài khoản không hợp lệ gọi AuthenticationEntryPoint bằng lỗi chung. Lỗi database/controller không bị biến thành lỗi token. Filter không ghi đè Authentication đã có nhưng vẫn kiểm tra Bearer được gửi; context do filter tạo được dọn sau request.

**Chưa có endpoint auth hoặc chain production trong phase 2.** Các kết quả 401 nêu trên đã kiểm tra qua filter fixture; phase 3 cần gắn filter đúng một lần, cung cấp JSON entrypoint/403 handler, tắt Basic/form login và thực thi bảng quyền endpoint. Không dùng kết quả filter fixture làm bằng chứng API hiện đã phục vụ JWT.

## 4.11. Hợp đồng thực tế sau phase 3

| Endpoint | Success | Ghi chú |
| --- | --- | --- |
| POST /api/auth/register | 201 UserResponse | Chưa verify, role USER; gửi OTP sau commit; không cấp token |
| POST /api/auth/verify-email | 204, không body | Request email + code dạng chuỗi 6 số |
| POST /api/auth/resend-verification | 202 message | Thông báo chung nếu không tồn tại/đã verify/INACTIVE; không gửi email ở các trường hợp đó |
| POST /api/auth/login | 200 LoginResponse | token, tokenType=Bearer, expiresIn giây và user an toàn |
| GET /api/auth/me | 200 CurrentUserResponse | JWT bắt buộc, không nhận userId từ client |

Verify request mẫu:

```json
{
  "email": "a@example.com",
  "code": "012345"
}
```

Resend request mẫu:

```json
{
  "email": "a@example.com"
}
```

Auth DTO từ chối trường không hỗ trợ bằng 400. Validation dùng chung AuthInputPolicy: email/name/phone được normalize theo quy tắc hiện có, password không trim; password đăng ký tối đa 72 byte UTF-8 thay vì 72 ký tự. Các response không có hash hoặc OTP; chỉ login trả token, Cache-Control: no-store. Không dùng session/cookie để xác thực.

Lỗi mẫu:

```json
{
  "timestamp": "2026-10-22T00:00:00Z",
  "status": 409,
  "error": "EMAIL_ALREADY_EXISTS",
  "message": "Email is already registered",
  "path": "/api/auth/register"
}
```

Mã lỗi thực tế: VALIDATION_ERROR/INVALID_REQUEST (400), INVALID_VERIFICATION_CODE (400), UNAUTHORIZED (401), FORBIDDEN (403), EMAIL_ALREADY_EXISTS (409), RATE_LIMITED (429), INTERNAL_ERROR (500), EMAIL_DELIVERY_UNAVAILABLE (503). Framework errors giữ 404 NOT_FOUND, 405 METHOD_NOT_ALLOWED, 415 UNSUPPORTED_MEDIA_TYPE khi đã qua security. details tùy chọn chỉ chứa tên trường/thông báo, không chứa dữ liệu bị từ chối. 401 có WWW-Authenticate: Bearer; 429 có Retry-After. Path không chứa query.

Security áp dụng bảng quyền plan 07 theo method/path; không mở toàn bộ /api/auth/**. Bearer sai trên public endpoint cũng trả 401. Các URI không được cấu hình bị deny nên có thể trả 401/403 trước khi tới MVC; không cam kết mọi URI lạ đều 404. Chỉ dispatcher ERROR nội bộ được phép đi tới error controller; request thường /error không public.

Rate limit IP/endpoint mặc định mỗi 60 giây: register 5, login 10, verify 20, resend 5; kiểm tra trước JWT/body. Request đã vượt quota trả 429 kể cả có Bearer hỏng. Dùng IP kết nối, không tin forwarded headers. Bộ đếm in-memory bounded theo AUTH_RATE_MAX_KEYS, không chia sẻ giữa instance. OTP theo tài khoản vẫn tối đa 5 lần sai/mã và resend cooldown 60 giây.

CORS chỉ cho origin được cấu hình rõ, credentials=false; origin bị từ chối nhận 403 JSON. Swagger public khi docs bật rõ (mặc định dev/test), môi trường khác tắt. OpenAPI có bearerAuth trên /auth/me, không yêu cầu Bearer cho public auth endpoints. Profile đã triển khai ở phase 4 (4.12). Các CRUD Admin nghiệp vụ ở mục 4.6 còn là thiết kế; rule security không đồng nghĩa đã có controller. Riêng API Trip công khai và Booking người dùng đã được triển khai, kiểm thử.

Mẫu thử local: requests/auth.http. Nghiệm thu: docs/07-auth-guide.md.

## 4.12. Contract đã triển khai ở phase 4

Mục này cập nhật các phần thiết kế cũ: profile đã triển khai và có ba role USER, ADMIN, SUPER_ADMIN.

| Method/path | Quyền | Kết quả |
| --- | --- | --- |
| GET /api/users/me | USER/ADMIN/SUPER_ADMIN đã xác minh, ACTIVE | 200 UserResponse |
| PUT /api/users/me | Như GET | 200 UserResponse |
| PUT /api/admin/users/{userId}/role | Chỉ SUPER_ADMIN | 200 UserResponse |

PUT profile yêu cầu cả fullName và phone, ví dụ `{"fullName":"Nguyễn Văn Huy Hoàng","phone":"0901234567"}`. Chỉ hai trường này được sửa; các trường lạ/read-only trả 400. Không dùng query id để chọn người khác. Cập nhật không thay email/password/role/status/emailVerified/createdAt; hai cập nhật profile đồng thời dùng last-write-wins.

PUT role nhận `{"role":"ADMIN"}` hoặc `{"role":"USER"}`. Nhân viên là USER đã đăng ký; nâng ADMIN chỉ khi ACTIVE và emailVerified=true (không đủ điều kiện trả 409 ACCOUNT_NOT_ELIGIBLE). Hạ quyền được phép cả khi inactive/chưa verify. Không tìm thấy target trả 404 NOT_FOUND; target SUPER_ADMIN trả 403; yêu cầu role SUPER_ADMIN trả 400. ADMIN thường/USER gọi endpoint trả 403. JWT hiện tại nhận quyền mới từ database ở request tiếp theo.

Public register luôn tạo USER. Chỉ bootstrap nội bộ dev/test tạo SUPER_ADMIN đầu tiên, vẫn OTP email trước login. Database chặn tài khoản SUPER_ADMIN thứ hai, kể cả super admin hiện tại inactive. Không có API cấp/thay thế/hạ SUPER_ADMIN. SUPER_ADMIN có quyền các route quản trị ADMIN; CRUD nghiệp vụ chưa được triển khai.

Các response profile/role có Cache-Control: no-store, không có hash/token. OpenAPI khai báo bearerAuth. Mẫu request: requests/profile.http và requests/super-admin.http.

## 4.13. Admin Trip & Booking — implemented contract (24/10/2026)

This section supersedes older planned-status statements for these five endpoints only.
All require a valid JWT for an ACTIVE, email-verified ADMIN or SUPER_ADMIN.
Anonymous/invalid JWT: `401`; USER: `403`. Existing role-management restrictions remain unchanged.

| Method/path | Success | Response / optional filters |
| --- | --- | --- |
| GET /api/admin/trips | 200 | TripResponse array; routeId, status, departureDate (ISO date) |
| POST /api/admin/trips | 201 | TripResponse; Location: /api/trips/{id} |
| PUT /api/admin/trips/{tripId} | 200 | TripResponse |
| DELETE /api/admin/trips/{tripId} | 200 | Cancelled TripResponse, including repeated cancellation |
| GET /api/admin/bookings | 200 | BookingResponse array; userId, tripId, status |

POST and PUT require the complete request body:

```json
{
  "routeId": 1,
  "vehicleId": 1,
  "departureTime": "2026-11-10T08:00:00",
  "arrivalTime": "2026-11-10T11:00:00",
  "price": 150000
}
```

IDs must be positive; Route and Vehicle must exist and be ACTIVE (a MAINTENANCE vehicle is ineligible).
Times are local date-times, consistent with the existing schema and public API; arrival must follow departure.
No new future-only departure restriction is imposed: the existing model has no such rule.
Price is `0` through `9999999999.99`, with at most two decimal places, matching NUMERIC(12,2).
Unknown body fields, including `availableSeats` and `status`, are rejected with `400 INVALID_REQUEST`.
Bean validation failures return `400 VALIDATION_ERROR`; invalid time order or ineligible references return `400 BUSINESS_ERROR`.
Missing Trip, Route or Vehicle returns `404 NOT_FOUND`.

Creation always initializes SCHEDULED and the full vehicle seat capacity.
Only SCHEDULED trips can be updated; other states return `409 TRIP_CONFLICT`.
Keeping the same vehicle preserves availableSeats. Changing vehicle sets availableSeats to its capacity
minus SUM(quantity) of CONFIRMED bookings; insufficient capacity returns `409 TRIP_CONFLICT`.
Existing bookings, including their historical prices, remain unchanged.

Cancellation is a **temporary Phase 1 policy**: a SCHEDULED trip with no CONFIRMED bookings can be soft-cancelled.
Confirmed bookings, IN_PROGRESS and COMPLETED trips cause `409 TRIP_CONFLICT`.
Cancelled booking history is retained; repeated cancellation returns the current CANCELLED trip.
There is no physical trip deletion, mass booking cancellation, refund, or notification workflow.
The final cancellation/refund policy requires business approval.

Lists return unpaginated arrays, with all optional filters combined using AND.
Trip departureDate covers [start of day, start of next day), ordered by departureTime then id ascending.
Bookings are ordered by bookedAt then id descending; BookingResponse does not add user credentials or account details.
Invalid enum/date/ID query syntax returns 400. Filters matching nothing return an empty array.
The public trip search's existing inclusion of all states is preserved; admin filtering adds route/status selection.

Admins use existing `GET /api/bookings/{bookingId}` and `DELETE /api/bookings/{bookingId}` across users.
Both ADMIN and SUPER_ADMIN are recognized. USER ownership checks and single-booking cancellation remain in BookingService.
No additional admin booking detail/status endpoints exist. OpenAPI reuses the existing `bearerAuth` scheme.

Swagger requests, safety analysis, changed files and verification results: [Admin implementation report](08-admin-trip-booking-report.md).
