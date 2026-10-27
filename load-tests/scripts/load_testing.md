# Hướng dẫn kiểm thử tải BusBooking trên Kaggle CPU

## Chuẩn bị

1. Trên Windows, build backend Spring Boot từ đúng commit cần đo:
   ```powershell
   .\mvnw.cmd -DskipTests clean package
   ```
   Chọn JAR chạy được trong `target/` (**không chọn `.jar.original`**).
2. Tạo **Private Kaggle Dataset** chứa JAR và một dataset chứa `busbooking_seed.sql` (chỉ dữ liệu giả lập).
3. Tạo Kaggle Notebook; chọn **Accelerator: None (CPU)**, **Internet: On**, rồi đính kèm hai dataset.
4. Import notebook tại `notebooks/busbooking-kaggle-cpu-test.ipynb`.

## Thứ tự chạy trong notebook

1. **Phần 1–5:** Cài Locust/Java/PostgreSQL, chọn JAR, khởi động backend; kiểm tra `GET /api/trips` trả HTTP 200.
2. **Phần 6:** Nạp seed SQL vào **database Kaggle riêng**; chỉnh `SEED_SQL_PATH` theo đường dẫn thực tế được tìm thấy dưới `/kaggle/input/`. **Chỉ import một lần** vào database mới.
3. **Phần 7–8:** Tạo Locust GET rồi đo **1, 5, 10, 20 users × 60 giây/mức**.
4. **Phần 9:** Lưu thống kê GET và manifest môi trường.
5. **Phần 10:** Đăng ký USER test, xác minh OTP bằng Mailpit, tạo **fixture Booking mới**. Dùng **kịch bản POST cuối** (`booking_clean_locust.py` hoặc `scripts/post_booking_locust.py`), với 1.000 Trip × 4 ghế; chạy **20 users × 60 giây**. **Bỏ qua lượt POST thử 200 Trip/800 ghế nếu chỉ muốn đo baseline chính thức**, vì lượt đó sẽ tạo HTTP 409 khi hết ghế.
6. **Phần 10.5:** Kiểm tra tính nhất quán số ghế (`Trips with incorrect seat balance: 0`).
7. **Phần biểu đồ và xuất báo cáo (nếu có trong notebook đã cập nhật):** Tạo PNG, CSV, ZIP cuối và tải từ Kaggle **Output**.

> **Không bấm Run All tùy tiện:** các cell seed Trip và POST Booking **thay đổi dữ liệu**. Nếu đã chạy một lượt, tạo fixture mới/khôi phục database thử nghiệm sạch trước khi đo lại. Không bao giờ trỏ Kaggle notebook vào PostgreSQL development hoặc production.

**Không đưa mật khẩu, JWT, OTP, `.env`, file dữ liệu người dùng thật hoặc JAR chứa cấu hình nhạy cảm lên GitHub công khai.**
