# Rà soát database index Pha 1

Ngày rà soát: 27/10/2026. Phạm vi: toàn bộ Flyway V1–V15 và các truy vấn, khóa, kiểm tra phụ thuộc trong mã nguồn hiện tại. Thay đổi được chuẩn bị trong V16; chưa triển khai lên database dev hoặc môi trường dùng chung.

## Trạng thái trước khi sửa

- Working tree ban đầu sạch. V12–V15 thuộc commit `bbda8f2`, được local tracking ref `origin/feat/complete-locations-operators-routes-vehicles` ghi nhận. Không viết lại các file đã commit này.
- Đối chiếu `.env`, cấu hình Spring, metadata Docker và cấu hình backend: database dev là `busbooking_dev_fresh`, container `busbooking-db`, PostgreSQL **16.15**, cổng host **5435**, user `busbooking_user`.
- Chỉ đọc catalog và `public.flyway_schema_history` qua `psql -X`, `ON_ERROR_STOP=1`, `PGOPTIONS='-c default_transaction_read_only=on'`, trong transaction `READ ONLY`. Không khởi động ứng dụng hay chạy Flyway migrate trên dev.
- V1–V15 đều `success=true`; V16 chưa có. Checksum V12–V15 lần lượt là `369453970`, `1056749905`, `14302262`, `-1970298983`.
- Bốn index mục tiêu đều là B-tree một cột, hợp lệ; không UNIQUE, không PRIMARY KEY, không replica identity, không dùng cho clustering. Không constraint nào có `conindid` trỏ tới chúng; không đối tượng nào phụ thuộc theo `pg_depend`.
- Không xác minh được trạng thái của mọi môi trường dùng chung từ workspace này. Bằng chứng đã áp dụng tại dev đủ để chọn migration bổ sung, giữ nguyên lịch sử V1–V15.

## Nhóm A: giữ nguyên

| Thành phần | Migration | Lý do |
| --- | --- | --- |
| PK của `roles`, `users`, `bus_operators`, `locations`, `vehicles`, `routes`, `trips`, `bookings`, `email_verifications` và index tương ứng | V1–V9 | Định danh duy nhất; các quan hệ và thao tác theo ID dựa vào chúng |
| `uk_roles_name` | V1 | Tên role duy nhất |
| `uk_users_email` | V2 | Email tài khoản duy nhất, kể cả đăng ký đồng thời |
| `uk_vehicles_license_plate` | V5 | Biển số đã chuẩn hóa phải duy nhất |
| `uk_bookings_code` | V8 | Mã booking duy nhất |
| `uk_users_single_super_admin` | V11 | Partial UNIQUE index giới hạn một SUPER_ADMIN |
| `uk_locations_normalized_identity` | V12 | Expression UNIQUE index chống trùng tên/tỉnh/địa chỉ không phân biệt hoa thường |
| `uk_bus_operators_normalized_name` | V13 | Expression UNIQUE index chống trùng tên nhà xe không phân biệt hoa thường |
| `uk_bus_operators_normalized_email` | V13 | UNIQUE index chống trùng email nhà xe đã chuẩn hóa |
| `uk_routes_origin_destination` | V14 | UNIQUE constraint bảo đảm cặp điểm đi–đến duy nhất; chiều ngược lại vẫn được phép |
| Toàn bộ 9 FOREIGN KEY | V2, V5–V9 | Bảo đảm tham chiếu hợp lệ; giữ cả hành vi cascade đã có của email verification |
| Toàn bộ 40 CHECK và mọi NOT NULL | V1–V15 | Validation trạng thái, số lượng, giá, thời gian, nội dung và dạng chuẩn hóa |
| Trigger `trg_users_updated_at` và hàm tương ứng | V2 | Giữ quy tắc thời điểm tạo/cập nhật |
| `flyway_schema_history_pk`, `flyway_schema_history_s_idx` | Flyway quản lý | Hạ tầng lịch sử migration, ngoài phạm vi tinh giản index nghiệp vụ |

Index hỗ trợ UNIQUE/PK vẫn có tác dụng tăng tốc nhưng đồng thời bảo đảm nghiệp vụ nên phải giữ. Việc phân loại dựa trên định nghĩa và phụ thuộc, không dựa trên tiền tố tên.

## Nhóm B: hoãn sang Pha 2

| Index | Định nghĩa | Mục đích hiện tại và ảnh hưởng khi hoãn |
| --- | --- | --- |
| `idx_routes_destination_id` | `routes(destination_id)` — V14 | Hỗ trợ lọc/join theo điểm đến, tìm route tham chiếu location khi sửa/xóa khóa cha. Có thể phải quét bảng nhiều hơn; FK vẫn được kiểm tra. UNIQUE cặp origin/destination giữ nguyên. |
| `idx_trips_route_id` | `trips(route_id)` — V14 | Hỗ trợ tìm chuyến theo route, `hasAnyTrips`, `hasOperationalTrips` và kiểm tra FK. Các phép EXISTS vẫn trả cùng kết quả, có thể chậm hơn. |
| `idx_vehicles_operator_id` | `vehicles(operator_id)` — V15 | Hỗ trợ danh sách xe theo operator và kiểm tra phụ thuộc đang hoạt động trong `hasOperationalDependencies`. Có thể tăng số dòng phải đọc. |
| `idx_trips_vehicle_id` | `trips(vehicle_id)` — V15 | Hỗ trợ tìm chuyến theo xe và các kiểm tra phụ thuộc của Vehicle/Operator. Có thể chậm hơn khi số chuyến lớn. |

Không có truy vấn ép dùng tên bốn index, `USING INDEX`, khai báo entity hay migration nào khác cần chúng. Các truy vấn kiểm tra quan hệ vẫn thực thi bình thường khi không có index hỗ trợ phía tham chiếu. Khóa bi quan Route/Vehicle/Trip/Booking theo ID và cập nhật số ghế có điều kiện vẫn dùng logic hiện có và PK được giữ nguyên. Không sửa cơ chế đồng thời.

Đây là quyết định giới hạn phạm vi tối ưu của Pha 1. Chưa đo mức ảnh hưởng; không khẳng định việc bỏ index cải thiện hiệu năng. Khi dữ liệu hoặc thời gian chờ vượt nhu cầu sử dụng, cần xem xét lại. Pha 2 chỉ bổ sung index có bằng chứng từ workload, kế hoạch truy vấn và số đo trên cùng phần cứng, dữ liệu, mức đồng thời và cấu hình ứng dụng.

## Phương án Flyway

Thêm [V16__defer_optional_catalog_indexes.sql](../src/main/resources/db/migration/V16__defer_optional_catalog_indexes.sql). Không chỉnh sửa V1–V15, không repair/baseline/reset database, không sửa bảng lịch sử.

Các câu lệnh thay đổi schema trong V16:

```sql
SET LOCAL lock_timeout = '5s';
DROP INDEX public.idx_routes_destination_id;
DROP INDEX public.idx_trips_route_id;
DROP INDEX public.idx_vehicles_operator_id;
DROP INDEX public.idx_trips_vehicle_id;
```

File migration còn có khối `DO` kiểm tra catalog trước khi DROP: nếu một tên mục tiêu đã trở thành UNIQUE, PRIMARY KEY, replica identity hoặc index clustering thì migration báo lỗi. Không dùng `CASCADE` hoặc `IF EXISTS`: phụ thuộc mới hoặc index bị thiếu phải được điều tra. Flyway thực thi V16 trong transaction PostgreSQL; nếu một DROP thất bại thì các DROP trước đó rollback. `lock_timeout` chỉ áp dụng trong transaction, giới hạn thời gian chờ khóa của DROP xuống 5 giây; không thay cấu hình database lâu dài.

Trước khi triển khai ở môi trường khác, cần kiểm tra đúng endpoint/database/schema, `flyway info`/`validate`, định nghĩa và phụ thuộc bốn index. File hiện tại áp dụng cho schema `public` đã xác minh; không giả định môi trường có schema tùy chỉnh cũng giống local. Không tự áp dụng migration lên môi trường dùng chung trong lần rà soát này.

Nếu cần khôi phục index, tạo migration tiếp theo có bốn `CREATE INDEX` như V14–V15; không xóa dòng lịch sử V16 hoặc sửa checksum. Cách tạo thường/concurrently và thời điểm triển khai cần dựa trên tải thực tế.

## Môi trường kiểm thử cách ly

- Tạo riêng container `busbooking-index-review-db-20261027`, image `postgres:16`, bind `127.0.0.1:55434`, user `index_review`, nhãn `busbooking.scope=index-review-20261027`. Xác nhận database ban đầu `busbooking_index_review` và database chạy suite cuối `busbooking_test` đều có **0 bảng public** trước khi ghi. Tên `busbooking_test` đáp ứng assertion an toàn có sẵn; endpoint vẫn thuộc container mới, không phải test cũ ở cổng 55433.
- Container có volume mới riêng, không gắn volume `busbooking_busbooking_data` hoặc volume của container test cũ.
- Tạo riêng Mailpit `busbooking-index-review-mailpit-20261027`, image `axllent/mailpit:v1.27.8`, bind loopback `1025`/`8025`. Smoke test gửi vào SMTP sink local này.
- Ghi đè tường minh cả `TEST_DB_*` và `SPRING_DATASOURCE_*` về database mới, cố định SMTP local. Không dùng URL dev hoặc database test hiện hữu.
- Các database nâng cấp/kiểm tra lỗi cũng được tạo mới trong chính container riêng này; không xóa hoặc reset database đã có.

## Kiểm tra migration và tính toàn vẹn

Harness tạm `target/index-review/IndexMigrationVerification.java` dùng Flyway **11.14.1** từ dependency của dự án và PostgreSQL thật. Kết quả **52 PASS, 0 FAIL**, log tại `target/index-review/migration-verification.log`:

- Cài V1–V15 từ đầu, tạo dữ liệu mẫu ở cả chín bảng nghiệp vụ, sau đó chỉ áp dụng V16 và gọi Flyway validate.
- So sánh chính xác toàn bộ giá trị dòng trước/sau nâng cấp: không thay đổi.
- So sánh định nghĩa constraint, cột/default/identity/nullability, trigger, function, sequence và checksum/lịch sử V1–V15: không thay đổi.
- So sánh bản đồ tên/định nghĩa index: đúng bốn index mục tiêu biến mất; các index khác giữ nguyên. Tổng index public giảm **24 → 20**, gồm hai index hạ tầng Flyway.
- Giữ đủ **9 PK nghiệp vụ** (10 nếu tính Flyway), **9 FK**, **5 UNIQUE constraint**, **4 UNIQUE index nghiệp vụ độc lập**, **40 CHECK**.
- Thử vi phạm cả chín quy tắc UNIQUE nghiệp vụ, cả chín FK và 16 CHECK đại diện, gồm toàn bộ chín CHECK chuẩn hóa của V12/V13/V15. PostgreSQL từ chối với SQLState `23505`, `23503`, `23514` và đúng tên constraint/index. Các phép thử dùng savepoint/rollback trong database mẫu.
- Kiểm tra index cùng tên bị đổi thành standalone UNIQUE: V16 từ chối, index UNIQUE được giữ nguyên.
- Kiểm tra index cuối bị đổi tên, làm DROP cuối thất bại sau các DROP trước: tất cả index, constraint và lịch sử trước V16 được khôi phục qua rollback; không có dòng version 16 đã áp dụng.

Harness và log nằm trong `target/` (gitignored), phục vụ bằng chứng chạy tại workspace này. Không thêm dependency, không sửa hay vô hiệu hóa test có sẵn.

## Kết quả bộ test hiện có và API

Chạy toàn bộ test, bao gồm các smoke `*IT` opt-in, với `TEST_DB_*` và `SPRING_DATASOURCE_*` trỏ tới `jdbc:postgresql://127.0.0.1:55434/busbooking_test`, username `index_review`, mật khẩu chỉ dùng cho container mới. SMTP được cố định tại `127.0.0.1:1025`, tắt auth/STARTTLS cho Mailpit local.

```powershell
mvn -o -B '-Dmaven.repo.local=C:/Users/THIN BOX/.m2/repository' '-Dtest=*Test,*Tests,*IT' verify
```

Đường dẫn cache Maven là cấu hình của máy kiểm thử này. Maven chạy offline bằng dependency đã có; không đổi `pom.xml` hay dependency. Đã cấp quyền dùng cache để Maven tạo file khóa; các lệnh Docker được giới hạn vào thao tác chỉ đọc hoặc container kiểm thử mới.

**Kết quả cuối: 285 PASS, 0 FAIL, 0 ERROR, 0 SKIPPED trong 41 test suite; BUILD SUCCESS**, hoàn thành lúc **00:34:23 ngày 27/10/2026 (UTC+7)**. Maven verify tạo executable JAR thành công. Log xác nhận cài mới đủ V1–V16 và Flyway validate thành công trên PostgreSQL 16.15.

| Nhóm test API/nghiệp vụ cần rà soát | PASS | FAIL/ERROR/SKIPPED |
| --- | ---: | --- |
| `OperatorManagementIntegrationTest` | 4 | 0/0/0 |
| `VehicleManagementIntegrationTest` | 7 | 0/0/0 |
| `LocationManagementIntegrationTest` | 5 | 0/0/0 |
| `RouteManagementIntegrationTest` | 6 | 0/0/0 |
| `AdminManagementIntegrationTest` — Admin Trip/Booking, validation, lifecycle, đồng thời | 24 | 0/0/0 |
| `TripBookingIntegrationTest` | 6 | 0/0/0 |
| `CoreCatalogWorkflowIntegrationTest` — workflow liên module và phân quyền | 2 | 0/0/0 |
| `AuthLiveHttpSmokeIT`, `SuperAdminLiveHttpSmokeIT`, `SmtpMailpitSmokeIT` | 3 | 0/0/0 |

Các test giữ nguyên đã kiểm tra tạo, đọc/lọc, cập nhật, chuẩn hóa, chống trùng, phân quyền và chặn thay đổi khi có phụ thuộc. Phạm vi DELETE dữ liệu nền tảng vẫn theo API contract hiện tại; không thêm endpoint để mở rộng phạm vi bài toán.

Lượt chạy đầu có **261 PASS, 24 FAIL, 0 ERROR, 0 SKIPPED**, vì database mới tên `busbooking_index_review` không đáp ứng assertion `current_database() = 'busbooking_test'` trong `AdminManagementIntegrationTest:43`. Đã tạo thêm database `busbooking_test` trống trong cùng container riêng, xác minh đúng endpoint và chạy lại toàn bộ suite. Không đổi assertion hoặc vô hiệu hóa test. Log lượt đầu vẫn được giữ tại `target/index-review/full-suite-initial-db-name-mismatch.log`.

Bằng chứng của lượt chạy cuối:

- `target/index-review/full-suite.log` — kết quả Maven và endpoint Flyway thực tế.
- `target/index-review/test-summary.json` — tổng hợp 41 suite thực sự xuất hiện trong log lượt cuối, tránh cộng các XML cũ còn trong `target/surefire-reports`.
- `target/index-review/final-surefire-reports/` — bản sao XML của đúng các suite vừa chạy.
- `target/index-review/test-readonly-final.log` — V1–V16 thành công, bốn index mục tiêu không còn, tổng 20 index và đủ constraint.
- `target/index-review/dev-readonly-final.log` — dev vẫn V15, checksum V1–V15 không đổi và đủ bốn index mục tiêu.

## File thay đổi và giới hạn

1. Thêm `src/main/resources/db/migration/V16__defer_optional_catalog_indexes.sql`.
2. Cập nhật hiện trạng trong `docs/02-database-design.md`.
3. Thêm báo cáo `docs/09-phase-one-index-review.md`.

Không thay đổi Controller, Service, Repository, entity, API contract, cấu hình ứng dụng, dependency, migration V1–V15 hoặc test hiện có. Các artifact kiểm chứng bổ sung nằm trong `target/`, không đưa vào lịch sử migration hay mã ứng dụng.

Rủi ro còn lại: khi dữ liệu lớn, truy vấn theo FK và kiểm tra phụ thuộc có thể quét nhiều dòng hơn, kéo dài thời gian giữ khóa. Chưa có benchmark để lượng hóa tác động. DROP thường cần khóa bảng; V16 sẽ thất bại và rollback nếu phải chờ khóa quá 5 giây. Schema drift, thiếu index hoặc schema khác `public` cần xử lý riêng trước khi triển khai. Không suy ra trạng thái mọi môi trường dùng chung từ kết quả local.

Đã dừng hai container kiểm thử do lượt này tạo sau khi đối chiếu ID và nhãn; giữ lại container, dữ liệu và volume để kiểm tra lại. Không xóa bảng, dữ liệu hoặc Docker volume của môi trường hiện hữu, không reset database. Các thao tác ghi/fixture cleanup của test chỉ chạy trong PostgreSQL mới đã xác minh. Migration V16 không có DML; dữ liệu mẫu được chứng minh giữ nguyên khi nâng cấp. Ngoài bốn index mục tiêu trong môi trường kiểm thử riêng, không thay đổi schema hay dữ liệu của dev/test hiện hữu; dev chỉ được đọc catalog và lịch sử Flyway.
