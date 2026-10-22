# 5. Thiết kế mã nguồn

**Trạng thái hiện tại:** auth/profile/role/OTP và các adapter đã triển khai; các lớp nghiệp vụ còn lại có thể vẫn là khung. Cấu trúc dự kiến không phải bằng chứng đã có CRUD. Business/domain độc lập framework; wiring ở config. BusBookingApplication loại UserDetailsServiceAutoConfiguration vì API chỉ dùng danh tính từ JWT filter.

## 5.1. Tổng quan thiết kế mã nguồn

Hệ thống đặt vé xe khách được phát triển bằng Java và Spring Boot theo kiến trúc phân lớp kết hợp với nguyên tắc Ports and Adapters nhằm tách biệt xử lý nghiệp vụ, giao tiếp HTTP và truy cập dữ liệu.

Mã nguồn được tổ chức thành các package chính:

* `api`: Tiếp nhận và xử lý các yêu cầu HTTP, quản lý DTO và chuyển đổi dữ liệu đầu vào/đầu ra.
* `business`: Chứa các dịch vụ xử lý nghiệp vụ và các interface định nghĩa cổng truy cập dữ liệu, dịch vụ bảo mật.
* `domain`: Chứa mô hình miền nghiệp vụ, các enum và ngoại lệ nghiệp vụ.
* `data`: Chịu trách nhiệm ánh xạ dữ liệu miền với cơ sở dữ liệu PostgreSQL thông qua Spring Data JPA.
* `security`: Quản lý xác thực JWT, phân quyền và mã hóa mật khẩu.
* `config`: Chứa cấu hình chung của hệ thống, bao gồm OpenAPI và thời gian.
* `exception`: Xử lý ngoại lệ tập trung và chuẩn hóa phản hồi lỗi API.

Thiết kế này hướng đến các mục tiêu:

* Tách biệt trách nhiệm giữa các thành phần.
* Giảm sự phụ thuộc trực tiếp giữa tầng nghiệp vụ và công nghệ lưu trữ dữ liệu.
* Tăng khả năng kiểm thử độc lập.
* Hỗ trợ bảo trì và mở rộng hệ thống.
* Đảm bảo các quy tắc nghiệp vụ được thực thi nhất quán.

## 5.2. Cấu trúc project (dự kiến)

Mã nguồn chính được đặt trong package gốc `com.example.busbooking`.

```text

bus-booking/
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties
│
├── docs/
│   ├── 01-overview-and-requirements.md
│   ├── 02-database-design.md
│   ├── 03-system-archiecture.md
│   ├── 04-api-design.md
│   ├── 05-source-code-design.md
│   └── 06-quality-attributes.md
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/carbooking/
│   │   │       ├── BusBookingApplication.java
│   │   │       │
│   │   │       ├── api/
│   │   │       │   ├── controller/
│   │   │       │   │   ├── AuthController.java
│   │   │       │   │   ├── UserController.java
│   │   │       │   │   ├── TripController.java
│   │   │       │   │   ├── BookingController.java
│   │   │       │   │   └── AdminController.java
│   │   │       │   │
│   │   │       │   ├── dto/
│   │   │       │   │   ├── auth/
│   │   │       │   │   │   ├── RegisterRequest.java
│   │   │       │   │   │   ├── LoginRequest.java
│   │   │       │   │   │   ├── LoginResponse.java
│   │   │       │   │   │   └── CurrentUserResponse.java
│   │   │       │   │   │
│   │   │       │   │   ├── user/
│   │   │       │   │   │   ├── UserResponse.java
│   │   │       │   │   │   └── UpdateProfileRequest.java
│   │   │       │   │   │
│   │   │       │   │   ├── operator/
│   │   │       │   │   │   ├── OperatorRequest.java
│   │   │       │   │   │   └── OperatorResponse.java
│   │   │       │   │   │
│   │   │       │   │   ├── location/
│   │   │       │   │   │   ├── LocationRequest.java
│   │   │       │   │   │   └── LocationResponse.java
│   │   │       │   │   │
│   │   │       │   │   ├── vehicle/
│   │   │       │   │   │   ├── VehicleRequest.java
│   │   │       │   │   │   └── VehicleResponse.java
│   │   │       │   │   │
│   │   │       │   │   ├── route/
│   │   │       │   │   │   ├── RouteRequest.java
│   │   │       │   │   │   └── RouteResponse.java
│   │   │       │   │   │
│   │   │       │   │   ├── trip/
│   │   │       │   │   │   ├── TripSearchRequest.java
│   │   │       │   │   │   ├── TripRequest.java
│   │   │       │   │   │   └── TripResponse.java
│   │   │       │   │   │
│   │   │       │   │   └── booking/
│   │   │       │   │       ├── BookingRequest.java
│   │   │       │   │       ├── BookingResponse.java
│   │   │       │   │       └── BookingHistoryResponse.java
│   │   │       │   │
│   │   │       │   └── mapper/
│   │   │       │       ├── UserDtoMapper.java
│   │   │       │       ├── TripDtoMapper.java
│   │   │       │       └── BookingDtoMapper.java
│   │   │       │
│   │   │       ├── business/
│   │   │       │   ├── service/
│   │   │       │   │   ├── AuthService.java
│   │   │       │   │   ├── UserService.java
│   │   │       │   │   ├── OperatorService.java
│   │   │       │   │   ├── LocationService.java
│   │   │       │   │   ├── VehicleService.java
│   │   │       │   │   ├── RouteService.java
│   │   │       │   │   ├── TripService.java
│   │   │       │   │   └── BookingService.java
│   │   │       │   │
│   │   │       │   └── port/
│   │   │       │       ├── repository/
│   │   │       │       │   ├── RoleRepository.java
│   │   │       │       │   ├── UserRepository.java
│   │   │       │       │   ├── BusOperatorRepository.java
│   │   │       │       │   ├── LocationRepository.java
│   │   │       │       │   ├── VehicleRepository.java
│   │   │       │       │   ├── RouteRepository.java
│   │   │       │       │   ├── TripRepository.java
│   │   │       │       │   └── BookingRepository.java
│   │   │       │       │
│   │   │       │       └── security/
│   │   │       │           ├── PasswordHasher.java
│   │   │       │           └── TokenProvider.java
│   │   │       │
│   │   │       ├── domain/
│   │   │       │   ├── model/
│   │   │       │   │   ├── Role.java
│   │   │       │   │   ├── User.java
│   │   │       │   │   ├── BusOperator.java
│   │   │       │   │   ├── Location.java
│   │   │       │   │   ├── Vehicle.java
│   │   │       │   │   ├── Route.java
│   │   │       │   │   ├── Trip.java
│   │   │       │   │   └── Booking.java
│   │   │       │   │
│   │   │       │   ├── enums/
│   │   │       │   │   ├── RoleName.java
│   │   │       │   │   ├── UserStatus.java
│   │   │       │   │   ├── OperatorStatus.java
│   │   │       │   │   ├── VehicleStatus.java
│   │   │       │   │   ├── RouteStatus.java
│   │   │       │   │   ├── TripStatus.java
│   │   │       │   │   └── BookingStatus.java
│   │   │       │   │
│   │   │       │   └── exception/
│   │   │       │       ├── BusinessException.java
│   │   │       │       ├── ResourceNotFoundException.java
│   │   │       │       ├── InsufficientSeatsException.java
│   │   │       │       └── ForbiddenOperationException.java
│   │   │       │
│   │   │       ├── data/
│   │   │       │   ├── entity/
│   │   │       │   │   ├── RoleEntity.java
│   │   │       │   │   ├── UserEntity.java
│   │   │       │   │   ├── BusOperatorEntity.java
│   │   │       │   │   ├── LocationEntity.java
│   │   │       │   │   ├── VehicleEntity.java
│   │   │       │   │   ├── RouteEntity.java
│   │   │       │   │   ├── TripEntity.java
│   │   │       │   │   └── BookingEntity.java
│   │   │       │   │
│   │   │       │   ├── repository/
│   │   │       │   │   ├── SpringDataRoleRepository.java
│   │   │       │   │   ├── SpringDataUserRepository.java
│   │   │       │   │   ├── SpringDataBusOperatorRepository.java
│   │   │       │   │   ├── SpringDataLocationRepository.java
│   │   │       │   │   ├── SpringDataVehicleRepository.java
│   │   │       │   │   ├── SpringDataRouteRepository.java
│   │   │       │   │   ├── SpringDataTripRepository.java
│   │   │       │   │   └── SpringDataBookingRepository.java
│   │   │       │   │
│   │   │       │   ├── adapter/
│   │   │       │   │   ├── RoleRepositoryAdapter.java
│   │   │       │   │   ├── UserRepositoryAdapter.java
│   │   │       │   │   ├── BusOperatorRepositoryAdapter.java
│   │   │       │   │   ├── LocationRepositoryAdapter.java
│   │   │       │   │   ├── VehicleRepositoryAdapter.java
│   │   │       │   │   ├── RouteRepositoryAdapter.java
│   │   │       │   │   ├── TripRepositoryAdapter.java
│   │   │       │   │   └── BookingRepositoryAdapter.java
│   │   │       │   │
│   │   │       │   └── mapper/
│   │   │       │       ├── RolePersistenceMapper.java
│   │   │       │       ├── UserPersistenceMapper.java
│   │   │       │       ├── TripPersistenceMapper.java
│   │   │       │       └── BookingPersistenceMapper.java
│   │   │       │
│   │   │       ├── security/
│   │   │       │   ├── SecurityConfig.java
│   │   │       │   ├── JwtAuthenticationFilter.java
│   │   │       │   ├── JwtService.java
│   │   │       │   ├── CustomUserDetailsService.java
│   │   │       │   └── PasswordEncoderConfig.java
│   │   │       │
│   │   │       ├── config/
│   │   │       │   ├── OpenApiConfig.java
│   │   │       │   └── TimeConfig.java
│   │   │       │
│   │   │       └── exception/
│   │   │           ├── GlobalExceptionHandler.java
│   │   │           └── ErrorResponse.java
│   │   │
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-dev.properties
│   │       ├── application-test.properties
│   │       └── db/
│   │           └── migration/
│   │               ├── V1__create_roles_and_users.sql
│   │               ├── V2__create_operators_and_vehicles.sql
│   │               ├── V3__create_locations_and_routes.sql
│   │               └── V4__create_trips_and_bookings.sql
│   │
│   └── test/
│       └── java/
│           └── com/example/carbooking/
│               ├── business/
│               │   ├── TripServiceTest.java
│               │   └── BookingServiceTest.java
│               ├── api/
│               │   ├── AuthControllerTest.java
│               │   ├── TripControllerTest.java
│               │   └── BookingControllerTest.java
│               ├── data/
│               │   └── BookingRepositoryAdapterTest.java
│               └── integration/
│                   ├── AuthIntegrationTest.java
│                   ├── TripIntegrationTest.java
│                   └── BookingIntegrationTest.java
│
├── .env.example
├── .gitignore
├── Dockerfile
├── docker-compose.yml
├── mvnw
├── mvnw.cmd
├── pom.xml
└── README.md
```

Mỗi package có phạm vi trách nhiệm riêng. Các thành phần chỉ nên đảm nhiệm chức năng thuộc tầng của mình, tránh đưa toàn bộ xử lý vào controller hoặc để tầng nghiệp vụ phụ thuộc trực tiếp vào framework truy cập dữ liệu.

## 5.3. API Layer

### 5.3.1. Controller

Package `api.controller` tiếp nhận yêu cầu HTTP, kiểm tra dữ liệu đầu vào ở mức API, gọi dịch vụ nghiệp vụ tương ứng và trả về phản hồi JSON.

| Class               | Trách nhiệm                                                                                            |
| ------------------- | ------------------------------------------------------------------------------------------------------ |
| `AuthController`    | Xử lý đăng ký, đăng nhập và lấy thông tin người dùng hiện tại.                                         |
| `UserController`    | Xử lý xem và cập nhật hồ sơ cá nhân, xem lịch sử đặt vé.                                               |
| `TripController`    | Tìm kiếm chuyến xe và xem chi tiết chuyến xe.                                                          |
| `BookingController` | Tạo, xem và hủy đặt vé.                                                                                |
| `AdminController`   | Cung cấp API quản trị người dùng, nhà xe, địa điểm, phương tiện, tuyến đường, chuyến xe và đơn đặt vé. |

Controller không trực tiếp truy vấn cơ sở dữ liệu, không chứa các quy tắc nghiệp vụ phức tạp và không tự thực hiện kiểm tra quyền truy cập lặp lại trong từng endpoint.

Các chức năng xác thực và phân quyền được xử lý tập trung tại Spring Security. Những quy tắc nghiệp vụ như kiểm tra số ghế còn trống hoặc quyền sở hữu đơn đặt vé được kiểm tra tại tầng Business.

### 5.3.2. DTO

Package `api.dto` chứa các đối tượng dùng để trao đổi dữ liệu giữa client và API. DTO được chia thành các nhóm chức năng:

| Package    | Các lớp tiêu biểu                                                         | Mục đích                                                   |
| ---------- | ------------------------------------------------------------------------- | ---------------------------------------------------------- |
| `auth`     | `RegisterRequest`, `LoginRequest`, `LoginResponse`, `CurrentUserResponse` | Trao đổi dữ liệu đăng ký, đăng nhập và thông tin xác thực. |
| `user`     | `UserResponse`, `UpdateProfileRequest`                                    | Xem và cập nhật hồ sơ người dùng.                          |
| `operator` | `OperatorRequest`, `OperatorResponse`                                     | Quản lý nhà xe.                                            |
| `location` | `LocationRequest`, `LocationResponse`                                     | Quản lý địa điểm.                                          |
| `vehicle`  | `VehicleRequest`, `VehicleResponse`                                       | Quản lý phương tiện.                                       |
| `route`    | `RouteRequest`, `RouteResponse`                                           | Quản lý tuyến đường.                                       |
| `trip`     | `TripSearchRequest`, `TripRequest`, `TripResponse`                        | Tìm kiếm và quản lý chuyến xe.                             |
| `booking`  | `BookingRequest`, `BookingResponse`, `BookingHistoryResponse`             | Tạo, xem và tra cứu đơn đặt vé.                            |

Sử dụng DTO giúp kiểm soát các trường dữ liệu được phép nhận và trả về, hạn chế việc để lộ cấu trúc nội bộ của domain model hoặc entity cơ sở dữ liệu.

Ví dụ, `LoginRequest` chỉ nhận email và mật khẩu; `LoginResponse` có thể trả về JWT cùng thông tin người dùng cần thiết, nhưng không bao giờ trả về mật khẩu đã mã hóa.

### 5.3.3. Mapper của API

Package `api.mapper` chịu trách nhiệm chuyển đổi dữ liệu giữa domain model và DTO.

| Class              | Trách nhiệm                                                  |
| ------------------ | ------------------------------------------------------------ |
| `UserDtoMapper`    | Chuyển đổi `User` thành `UserResponse` và ngược lại khi cần. |
| `TripDtoMapper`    | Chuyển đổi dữ liệu chuyến xe giữa domain model và DTO.       |
| `BookingDtoMapper` | Chuyển đổi dữ liệu đơn đặt vé giữa domain model và DTO.      |

Mapper chỉ thực hiện chuyển đổi dữ liệu, không chứa quy tắc nghiệp vụ như xác định giá vé, kiểm tra số ghế hoặc quyết định người dùng có được hủy đặt vé hay không.

## 5.4. Business Layer

### 5.4.1. Service

Package `business.service` triển khai các trường hợp sử dụng và quy tắc nghiệp vụ của hệ thống.

| Class             | Trách nhiệm chính                                                                              |
| ----------------- | ---------------------------------------------------------------------------------------------- |
| `AuthService`     | Xử lý đăng ký, đăng nhập, kiểm tra thông tin xác thực và tạo token thông qua các cổng bảo mật. |
| `UserService`     | Xem và cập nhật hồ sơ, kiểm soát quyền truy cập thông tin người dùng.                          |
| `OperatorService` | Quản lý thông tin nhà xe.                                                                      |
| `LocationService` | Quản lý địa điểm xuất phát và điểm đến.                                                        |
| `VehicleService`  | Quản lý phương tiện, sức chứa và trạng thái phương tiện.                                       |
| `RouteService`    | Quản lý tuyến đường và kiểm tra tính hợp lệ của địa điểm đầu/cuối.                             |
| `TripService`     | Tìm kiếm, xem chi tiết và quản lý chuyến xe.                                                   |
| `BookingService`  | Tạo, tra cứu và hủy đặt vé; kiểm tra số ghế còn trống và cập nhật số ghế.                      |

Các service phối hợp với domain model và các interface được định nghĩa trong `business.port`. Tầng này không trực tiếp gọi controller, không phụ thuộc vào Spring Web và không sử dụng Spring Data JPA hoặc các entity persistence.

### 5.4.2. Repository ports

Package `business.port.repository` định nghĩa các interface truy cập dữ liệu mà tầng nghiệp vụ cần sử dụng.

| Interface               | Trách nhiệm                          |
| ----------------------- | ------------------------------------ |
| `RoleRepository`        | Tra cứu vai trò người dùng.          |
| `UserRepository`        | Tra cứu, lưu và cập nhật người dùng. |
| `BusOperatorRepository` | Truy cập dữ liệu nhà xe.             |
| `LocationRepository`    | Truy cập dữ liệu địa điểm.           |
| `VehicleRepository`     | Truy cập dữ liệu phương tiện.        |
| `RouteRepository`       | Truy cập dữ liệu tuyến đường.        |
| `TripRepository`        | Truy cập và tìm kiếm chuyến xe.      |
| `BookingRepository`     | Truy cập dữ liệu đơn đặt vé.         |

Các interface này sử dụng domain model hoặc kiểu dữ liệu thuần của Java làm đầu vào và đầu ra. Chúng không kế thừa `JpaRepository` và không sử dụng annotation của tầng persistence.

Ví dụ minh họa:

```java
package com.example.busbooking.business.port.repository;

import com.example.busbooking.domain.model.Trip;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TripRepository {

    Optional<Trip> findById(Long tripId);

    List<Trip> search(
            Long originId,
            Long destinationId,
            LocalDate departureDate
    );

    Trip save(Trip trip);
}
```

Đây là interface nghiệp vụ cần để truy cập chuyến xe. Việc truy vấn thực tế bằng Spring Data JPA được triển khai ở tầng Data Access.

### 5.4.3. Security ports

Package `business.port.security` định nghĩa các dịch vụ bảo mật mà nghiệp vụ cần nhưng không cần biết cách triển khai cụ thể.

* `PasswordHasher`: cung cấp chức năng mã hóa và kiểm tra mật khẩu.
* `TokenProvider`: cung cấp chức năng tạo token xác thực sau khi đăng nhập thành công.

Các interface này giúp `AuthService` không phụ thuộc trực tiếp vào một thư viện mã hóa mật khẩu hoặc thư viện JWT cụ thể. Phần triển khai được cung cấp bởi các thành phần hạ tầng và cấu hình bảo mật.

## 5.5. Domain Layer

### 5.5.1. Domain model

Package `domain.model` chứa các đối tượng đại diện cho những khái niệm nghiệp vụ cốt lõi:

| Class         | Ý nghĩa                                                                           |
| ------------- | --------------------------------------------------------------------------------- |
| `Role`        | Vai trò của tài khoản.                                                            |
| `User`        | Người sử dụng hệ thống.                                                           |
| `BusOperator` | Đơn vị vận hành xe khách.                                                         |
| `Location`    | Địa điểm xuất phát hoặc điểm đến.                                                 |
| `Vehicle`     | Phương tiện được sử dụng để chạy chuyến xe.                                       |
| `Route`       | Tuyến đường nối địa điểm xuất phát với điểm đến.                                  |
| `Trip`        | Một chuyến xe cụ thể, có thời gian chạy, phương tiện, giá vé và số ghế còn trống. |
| `Booking`     | Đơn đặt vé của người dùng cho một chuyến xe.                                      |

Domain model không được gắn với cấu trúc ánh xạ của JPA. Các quan hệ giữa chúng được thể hiện thông qua các thuộc tính và định danh nghiệp vụ phù hợp.

Cần phân biệt `Route` và `Trip`: `Route` mô tả tuyến đường, chẳng hạn TP.HCM – Đà Lạt; `Trip` mô tả một lần khởi hành cụ thể trên tuyến đường đó.

### 5.5.2. Enum

Package `domain.enums` định nghĩa các giá trị trạng thái được phép sử dụng trong nghiệp vụ:

| Enum             | Ví dụ giá trị                         |
| ---------------- | ------------------------------------- |
| `RoleName`       | `USER`, `ADMIN`                       |
| `UserStatus`     | `ACTIVE`, `INACTIVE`                  |
| `OperatorStatus` | `ACTIVE`, `INACTIVE`                  |
| `VehicleStatus`  | `ACTIVE`, `INACTIVE`, `MAINTENANCE`   |
| `RouteStatus`    | `ACTIVE`, `INACTIVE`                  |
| `TripStatus`     | `SCHEDULED`, `CANCELLED`, `COMPLETED` |
| `BookingStatus`  | `CONFIRMED`, `CANCELLED`              |

Các giá trị trên là tập giá trị dự kiến cho thiết kế. Cần thống nhất chúng với database migration, entity mapping và API specification trước khi triển khai.

Sử dụng enum giúp hạn chế giá trị trạng thái tùy ý và làm rõ các trạng thái hợp lệ của từng đối tượng.

### 5.5.3. Domain exception

Package `domain.exception` chứa các ngoại lệ biểu diễn lỗi nghiệp vụ:

* `BusinessException`: ngoại lệ cơ sở cho các lỗi nghiệp vụ.
* `ResourceNotFoundException`: tài nguyên được yêu cầu không tồn tại.
* `InsufficientSeatsException`: số ghế còn trống không đủ để đáp ứng yêu cầu đặt vé.
* `ForbiddenOperationException`: thao tác không được phép theo quy tắc nghiệp vụ, chẳng hạn người dùng cố gắng hủy đơn đặt vé không thuộc quyền sở hữu của mình.

Các ngoại lệ này không trực tiếp tạo phản hồi HTTP. Việc chuyển ngoại lệ thành mã trạng thái và JSON response được xử lý ở tầng API thông qua cơ chế xử lý ngoại lệ tập trung.

## 5.6. Data Access Layer

### 5.6.1. Entity

Package `data.entity` chứa các lớp ánh xạ với các bảng trong PostgreSQL bằng JPA.

| Entity              | Bảng tương ứng  |
| ------------------- | --------------- |
| `RoleEntity`        | `roles`         |
| `UserEntity`        | `users`         |
| `BusOperatorEntity` | `bus_operators` |
| `LocationEntity`    | `locations`     |
| `VehicleEntity`     | `vehicles`      |
| `RouteEntity`       | `routes`        |
| `TripEntity`        | `trips`         |
| `BookingEntity`     | `bookings`      |

Các entity khai báo khóa chính, cột dữ liệu, quan hệ khóa ngoại và các ràng buộc cần thiết. Annotation như `@Entity`, `@Table`, `@Id`, `@ManyToOne` và `@JoinColumn` chỉ được sử dụng tại tầng này.

Entity không được trả về trực tiếp từ controller. Dữ liệu cần đi qua mapper để chuyển đổi thành domain model hoặc DTO phù hợp.

### 5.6.2. Spring Data repositories

Package `data.repository` chứa các interface kế thừa Spring Data JPA, ví dụ:

* `SpringDataUserRepository`
* `SpringDataTripRepository`
* `SpringDataBookingRepository`

Các interface này cung cấp các thao tác truy vấn và lưu dữ liệu thông qua JPA. Những truy vấn phức tạp, chẳng hạn tìm chuyến theo điểm đi, điểm đến và ngày khởi hành, có thể được định nghĩa bằng derived query hoặc `@Query`.

Các interface này chỉ được sử dụng trong tầng Data Access, không được gọi trực tiếp từ `business.service`.

### 5.6.3. Repository adapters

Package `data.adapter` triển khai các interface repository ports của tầng Business bằng cách sử dụng Spring Data repositories.

Ví dụ:

* `UserRepositoryAdapter` triển khai `UserRepository`.
* `TripRepositoryAdapter` triển khai `TripRepository`.
* `BookingRepositoryAdapter` triển khai `BookingRepository`.

Adapter chịu trách nhiệm:

1. Nhận yêu cầu truy cập dữ liệu từ tầng Business.
2. Gọi Spring Data repository phù hợp.
3. Chuyển đổi domain model thành entity khi lưu dữ liệu.
4. Chuyển đổi entity thành domain model khi đọc dữ liệu.
5. Trả kết quả về tầng Business.

Nhờ đó, tầng nghiệp vụ chỉ làm việc với các interface trừu tượng và domain model, không cần biết PostgreSQL hoặc JPA được sử dụng như thế nào.

### 5.6.4. Persistence mapper

Package `data.mapper` thực hiện chuyển đổi giữa domain model và JPA entity.

Các lớp dự kiến gồm:

* `RolePersistenceMapper`
* `UserPersistenceMapper`
* `TripPersistenceMapper`
* `BookingPersistenceMapper`

Để bảo đảm thiết kế nhất quán, cần bổ sung mapper tương ứng cho `BusOperator`, `Location`, `Vehicle` và `Route` khi các adapter này cần chuyển đổi dữ liệu. Không nên đưa logic chuyển đổi phức tạp vào repository adapter.

## 5.7. Security Layer

Package `security` chịu trách nhiệm triển khai cơ chế xác thực và phân quyền bằng Spring Security và JWT.

| Class                      | Trách nhiệm                                                                                          |
| -------------------------- | ---------------------------------------------------------------------------------------------------- |
| `SecurityConfig`           | Cấu hình các endpoint công khai, endpoint yêu cầu đăng nhập và endpoint chỉ dành cho ADMIN.          |
| `JwtAuthenticationFilter`  | Đọc Bearer token từ HTTP request, kiểm tra token và thiết lập thông tin xác thực cho request hợp lệ. |
| `JwtService`               | Tạo, phân tích và kiểm tra JWT.                                                                      |
| `CurrentUserLoader`       | Tải user theo ID, yêu cầu ACTIVE và emailVerified, tạo principal an toàn.                                         |
| `PasswordEncoderConfig`    | Cấu hình bộ mã hóa mật khẩu, chẳng hạn BCrypt.                                                       |

Luồng xác thực:

1. Người dùng gửi email và mật khẩu đến `AuthController`.
2. `AuthService` kiểm tra thông tin đăng nhập thông qua các cổng nghiệp vụ và bảo mật.
3. Khi xác thực thành công, hệ thống tạo JWT và trả về cho client.
4. Client gửi JWT trong header `Authorization: Bearer <token>` ở các request cần xác thực.
5. `JwtAuthenticationFilter` kiểm tra token và thiết lập danh tính người dùng.
6. Spring Security kiểm tra quyền truy cập trước khi request được chuyển đến controller.
7. Business Layer tiếp tục kiểm tra các quy tắc nghiệp vụ và quyền sở hữu tài nguyên khi cần.

Phân quyền theo endpoint được thực hiện tập trung, tránh lặp lại việc xác minh token trong từng controller. Tuy nhiên, phân quyền theo vai trò không thay thế việc kiểm tra quyền sở hữu tài nguyên. Ví dụ, `BookingService` vẫn phải kiểm tra người dùng có quyền xem hoặc hủy đơn đặt vé được yêu cầu hay không.

## 5.8. Cấu hình hệ thống và xử lý ngoại lệ

### 5.8.1. Config

Package `config` chứa các cấu hình dùng chung:

* `OpenApiConfig`: cấu hình tài liệu OpenAPI/Swagger, bao gồm cơ chế khai báo JWT Bearer để thử nghiệm endpoint được bảo vệ.
* `TimeConfig`: tập trung cấu hình các thành phần liên quan đến thời gian nếu hệ thống cần một nguồn thời gian có thể thay thế hoặc kiểm thử.

Cấu hình môi trường được đặt trong `src/main/resources`, với các file `application.properties`, `application-dev.properties` và `application-test.properties`. Thông tin nhạy cảm như mật khẩu database và JWT secret không được ghi trực tiếp vào mã nguồn hoặc commit lên GitHub.

### 5.8.2. Global exception handling

Package `exception` chứa:

* `GlobalExceptionHandler`: tiếp nhận và ánh xạ ngoại lệ thành phản hồi HTTP.
* `ErrorResponse`: cấu trúc JSON chuẩn cho lỗi API.

Ví dụ cấu trúc phản hồi:

```json
{
  "timestamp": "2026-10-09T10:00:00",
  "status": 400,
  "error": "INSUFFICIENT_SEATS",
  "message": "Not enough seats available",
  "path": "/api/bookings"
}
```

Mã HTTP thực tế phụ thuộc loại lỗi. Ví dụ, dữ liệu đầu vào không hợp lệ có thể trả về `400 Bad Request`, tài nguyên không tồn tại trả về `404 Not Found`, request chưa xác thực trả về `401 Unauthorized` và request không có quyền trả về `403 Forbidden`.

Các lỗi xác thực do Spring Security từ chối trước khi vào controller cần được cấu hình cơ chế phản hồi thống nhất tương ứng, vì chúng không nhất thiết đi qua `GlobalExceptionHandler`.

## 5.9. Luồng xử lý nghiệp vụ đặt vé

Chức năng đặt vé là luồng nghiệp vụ quan trọng nhất của hệ thống vì liên quan trực tiếp đến số ghế còn trống và dữ liệu đặt vé.

Luồng xử lý:

1. Client gửi `POST /api/bookings` kèm thông tin chuyến xe và số lượng vé.
2. Spring Security xác thực JWT và xác định người dùng hiện tại.
3. `BookingController` kiểm tra dữ liệu request ở mức API và gọi `BookingService`.
4. `BookingService` tải chuyến xe thông qua `TripRepository`.
5. Service kiểm tra chuyến xe tồn tại, đang ở trạng thái cho phép đặt vé và có đủ số ghế.
6. Service tạo `Booking`, lưu lại giá vé tại thời điểm đặt và tính tổng tiền.
7. Service cập nhật số ghế còn trống của chuyến xe.
8. Thông tin đặt vé và số ghế phải được lưu trong cùng một transaction.
9. Adapter sử dụng Spring Data JPA để thực hiện các thao tác truy cập PostgreSQL.
10. Kết quả được chuyển đổi thành `BookingResponse` và trả về client.

Khi hủy đặt vé, hệ thống phải kiểm tra đơn đặt vé tồn tại, trạng thái đơn hợp lệ và người thực hiện có quyền hủy. Nếu đơn được hủy thành công, số ghế tương ứng được hoàn lại đúng một lần.

Để tránh hai request đồng thời đặt vượt quá số ghế còn lại, việc kiểm tra và cập nhật số ghế cần có cơ chế bảo đảm tính nhất quán, chẳng hạn optimistic/pessimistic locking hoặc câu lệnh cập nhật có điều kiện. Chỉ sử dụng transaction mà không có biện pháp xử lý đồng thời phù hợp chưa chắc đã ngăn được đặt vé vượt sức chứa.

## 5.10. Quy tắc phụ thuộc giữa các layer

Hệ thống áp dụng quy tắc phụ thuộc hướng vào nghiệp vụ:

```text
API Layer
    │
    ▼
Business Layer ─────────► Domain Layer
    │
    ▼
Repository Ports
    ▲
    │ implements
Data Access Layer
    │
    ▼
PostgreSQL

Security Layer: xác thực request và cung cấp thông tin danh tính
Config Layer: cấu hình các thành phần của ứng dụng
Exception Layer: chuẩn hóa phản hồi lỗi API
```

Các quy tắc bắt buộc:

1. `api.controller` gọi các dịch vụ nghiệp vụ, không truy cập Spring Data repository trực tiếp.
2. `business.service` phụ thuộc vào domain model và các interface trong `business.port`, không phụ thuộc vào controller, Spring Web, JPA entity hoặc Spring Data repository.
3. `business.port.repository` chỉ định nghĩa hợp đồng truy cập dữ liệu, không triển khai chi tiết lưu trữ.
4. `data.adapter` triển khai repository ports và sử dụng `data.repository`.
5. `data.entity` và các persistence mapper thuộc tầng Data Access, không được đưa vào hợp đồng của Business Layer.
6. `domain` không phụ thuộc vào các tầng API, Data Access hoặc Security.
7. Các thành phần bảo mật và cấu hình kết nối với ứng dụng thông qua các interface hoặc cơ chế cấu hình phù hợp.
8. Việc xử lý lỗi HTTP được đặt ở tầng API, trong khi các lỗi nghiệp vụ được biểu diễn bằng domain exception.

Để đáp ứng nghiêm ngặt yêu cầu tách biệt Business Layer khỏi framework, các service nghiệp vụ không nên sử dụng trực tiếp annotation của Spring Web hoặc Spring Data. Có thể khai báo việc khởi tạo và liên kết các service, adapter thông qua các cấu hình bean ở tầng cấu hình ứng dụng.

## 5.11. Thiết kế kiểm thử

Các bài kiểm thử được phân chia theo phạm vi trách nhiệm:

| Thư mục            | Test tiêu biểu                                                         | Mục tiêu                                                                         |
| ------------------ | ---------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| `test/business`    | `TripServiceTest`, `BookingServiceTest`                                | Kiểm tra quy tắc nghiệp vụ độc lập, sử dụng repository ports giả lập khi cần.    |
| `test/api`         | `AuthControllerTest`, `TripControllerTest`, `BookingControllerTest`    | Kiểm tra endpoint, dữ liệu request/response và mã HTTP.                          |
| `test/data`        | `BookingRepositoryAdapterTest`                                         | Kiểm tra việc chuyển đổi dữ liệu và triển khai repository ports.                 |
| `test/integration` | `AuthIntegrationTest`, `TripIntegrationTest`, `BookingIntegrationTest` | Kiểm tra sự phối hợp giữa các tầng, bảo mật và database trong các luồng thực tế. |

Các tình huống cần ưu tiên kiểm thử:

* Đăng ký với email đã tồn tại.
* Đăng nhập với thông tin không hợp lệ.
* Truy cập endpoint được bảo vệ khi không có JWT.
* Người dùng có vai trò USER truy cập endpoint chỉ dành cho ADMIN.
* Tìm kiếm chuyến theo địa điểm và ngày khởi hành.
* Đặt vé khi chuyến xe không đủ số ghế.
* Hai yêu cầu đặt vé đồng thời trên chuyến xe có ít ghế còn lại.
* Người dùng cố gắng xem hoặc hủy đơn đặt vé của người khác.
* Hủy đơn đặt vé và hoàn lại số ghế đúng một lần.
* Lỗi database được chuyển thành phản hồi phù hợp, không để lộ thông tin nhạy cảm.


### Cập nhật triển khai security phase 2

Lớp rỗng CustomUserDetailsService được thay bằng CurrentUserLoader.loadById; flow JWT không dùng username/password authentication của Spring. AuthenticatedPrincipal chỉ có userId/email/role và implements Principal, không chứa hash hoặc UserEntity. AuthConfig wiring AuthService/JwtService/CurrentUserLoader; JwtProperties được bind ở config và validate secret khi tạo JwtService. Ghi chú này thuộc lịch sử phase 2; ở trạng thái hiện tại phase 3 đã gắn filter vào security chain đúng một lần, không đăng ký servlet filter bean.


## Cập nhật auth phase 4

UserController/UserService cung cấp profile; port updateProfile và conditional JPQL chỉ sửa fullName/phone. RoleManagementController/Service và RoleManagementRepositoryAdapter quản lý USER ↔ ADMIN với transaction khóa actor/target và kiểm tra lại eligibility. BootstrapSuperAdminService thuần business, BootstrapSuperAdminProperties và UserConfig ApplicationRunner tạo một super admin dev/test opt-in qua luồng OTP hiện có. Migration V11 bảo đảm tối đa một SUPER_ADMIN. Báo cáo và contract hiện tại: docs/07-auth-guide.md, mục 4.12 docs/04-api-design.md.
