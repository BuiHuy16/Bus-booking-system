# Kết quả kiểm thử tải Pha 1

**Ngày kiểm thử:** 27/10/2026  
**Môi trường:** Kaggle Notebook - CPU, không sử dụng GPU  
**Hệ thống:** BusBooking (Java/Spring Boot, PostgreSQL)  
**Công cụ:** Locust (Python), `psutil`; output notebook ghi `psutil 5.9.5` dù cell cài yêu cầu `>=6,<9` — nguyên nhân khác biệt phiên bản chưa xác minh.  
**Mục đích:** ghi nhận hiệu năng gốc (*baseline*) để nhóm Pha 2 đối chiếu sau khi cải tiến kiến trúc.

> Đây là kết quả các lượt kiểm thử đã thực hiện, không phải giới hạn chịu tải tối đa hay cam kết hiệu năng. Xem [hướng dẫn chạy kiểm thử tải](./scripts/load_testing.md) để tái lập.

## 1. Thiết lập kiểm thử

| Hạng mục | Thiết lập |
|---|---|
| Máy chạy | Một phiên Kaggle CPU; backend, PostgreSQL và Locust dùng chung tài nguyên |
| Backend | Spring Boot chạy từ JAR trên Kaggle |
| Database | PostgreSQL riêng trên Kaggle; fixture GET gồm 5 Trip, 2 Route, 3 Vehicle, 2 Operator, 4 Location; dữ liệu giả lập |
| Workload đọc | `GET /api/trips` và `GET /api/trips/{id}` với trọng số 3:1; detail cố định Trip ID 3, trạng thái `CANCELLED` |
| Tải GET | 1, 5, 10, 20 virtual users; 60 giây mỗi mức |
| Workload ghi | `POST /api/bookings`, đặt một vé/request với JWT của một USER test |
| Tải POST chính thức | 20 virtual users; 60 giây; 1.000 Trip mới, mỗi Trip 4 ghế |
| Ramp-up | Tối đa 5 users/giây |
| Wait time | Ngẫu nhiên 0,2–0,6 giây giữa các request |
| Theo dõi tài nguyên | `psutil.Process` của Java, lấy mẫu khoảng 1 giây khi Locust còn chạy; CPU = trung bình `cpu_percent(interval=None)` chưa chia số CPU logic; RAM = max(RSS/1024²), đơn vị MiB |

[`manifest.json`](results/busbooking_baseline/manifest.json) ghi Intel Xeon @ 2,20 GHz, 4 CPU logic, Java 21.0.13, PostgreSQL client 16.15 và Locust 2.47.0. SHA-256 JAR được đối chiếu khớp artifact hiện có trong `target/`; Git commit tạo JAR, trạng thái source lúc build và hash fixture/script chưa được ghi nhận, nên liên hệ với commit source hiện tại **chưa xác minh**.


## 2. Kết quả GET Trip

| Users | Requests (CSV) | Failures | RPS (CSV) | Avg (ms) | p50 (ms) | p95 (ms) | p99 (ms) | Java CPU TB | Java RAM tối đa |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 142 | 0 | 2,42 | 11,03 | 10 | 16 | 21 | 4,92% | 522,21 MiB |
| 5 | 705 | 0 | 11,94 | 7,44 | 7 | 10 | 13 | 11,86% | 532,97 MiB |
| 10 | 1.459 | 0 | 24,70 | 5,94 | 6 | 8 | 11 | 19,11% | 543,71 MiB |
| 20 | 2.834 | 0 | 48,01 | 5,09 | 5 | 6 | 9 | 19,44% | 543,86 MiB |

**Tổng snapshot CSV GET: 5.140 request, 0 lỗi Locust.** Bảng và biểu đồ dùng CSV/history; console cuối lượt lần lượt ghi **143, 714, 1.472, 2.864 request** (tổng **5.193**) và **2,41; 11,97; 24,71; 48,05 RPS** cho 1/5/10/20 users. Nguyên nhân chênh lệch và mốc thống kê chuẩn **chưa xác minh**; không coi snapshot CSV là tổng cuối toàn lượt. Trong CSV, thông lượng tăng ở khoảng 1–20 users; độ trễ giảm có thể liên quan JVM warm-up/JIT và cache, không chứng minh nhiều users làm hệ thống nhanh hơn.

### Biểu đồ GET

![RPS theo số người dùng](results/busbooking_baseline/get_throughput.png)

![p95 theo số người dùng](results/busbooking_baseline/get_p95_latency.png)

## 3. Kết quả POST Booking

### 3.1. Lượt kiểm tra giới hạn ghế

Kịch bản cũ có code tạo 200 Trip và POST ngẫu nhiên; mô tả trước đây ghi **200 Trip × 4 ghế = 800 ghế** và các số dưới đây. **CHƯA XÁC MINH:** không tìm thấy CSV/log hoặc output notebook của lượt cũ để xác nhận kết quả bảng:

| Requests | Booking thành công | HTTP 409 | Ghế còn lại |
|---:|---:|---:|---:|
| 2.832 | 800 | 2.032 | 0 |

Thông tin cả 200 Trip hết ghế và số HTTP 409 ở bảng trên **chưa xác minh**. Với `409 INSUFFICIENT_SEATS`, hệ thống từ chối do thiếu ghế, **không nên tính như sự cố hạ tầng**; không suy ra mọi HTTP 409 đều do hết ghế. Lượt cũ **không dùng làm baseline throughput POST chính thức**.

### 3.2. Lượt benchmark POST chính thức

Notebook ghi đã tạo fixture mới **1.000 Trip × 4 ghế = 4.000 ghế**. Locust phân phối tối đa bốn yêu cầu đặt một vé tới mỗi Trip. Bảng dùng snapshot CSV **2.789 request / 47,21 RPS**; console cuối lượt ghi **2.816 / 47,16 RPS**, cũng 0 lỗi. Nguyên nhân chênh lệch và mốc thống kê chuẩn **chưa xác minh**.


| Users | Thời gian | Requests (CSV) | Failures | RPS (CSV) | Avg (ms) | p50 (ms) | p95 (ms) | p99 (ms) | Java CPU TB | Java RAM tối đa |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 20 | 60s | 2.789 | 0 | 47,21 | 16,09 | 15 | 25 | 40 | 83,02% | 643,30 MiB |


**Kiểm tra tính nhất quán dữ liệu:** `Trips with incorrect seat balance: 0`. Trong 1.000 Trip thử nghiệm, không phát hiện trường hợp `available_seats + tổng vé CONFIRMED` khác sức chứa xe. Đây là kiểm chứng của workload đã chạy, không phải bằng chứng tuyệt đối rằng không có race condition trong mọi kịch bản.


## 4. So sánh hai workload tại 20 virtual users

| Chỉ số | GET Trip | POST Booking |
|---|---:|---:|
| Requests (snapshot CSV) | 2.834 | 2.789 |
| Throughput (CSV) | 48,01 RPS | 47,21 RPS |
| Thời gian phản hồi trung bình | 5,09 ms | 16,09 ms |
| p95 | 6 ms | 25 ms |
| Tỷ lệ lỗi | 0% | 0% |
| Java CPU trung bình | 19,44% | 83,02% |
| Java RAM RSS tối đa | 543,86 MiB | 643,30 MiB |


Bảng so sánh dùng snapshot CSV, chưa chốt là thống kê cuối lượt do khác console. POST Booking có JWT, transaction và ghi dữ liệu; đây có thể là các yếu tố liên quan đến độ trễ/CPU cao hơn, chưa có profiling để xác nhận nguyên nhân. Hai workload khác nhau, nên bảng này **không phải phép so sánh hai kiến trúc**.

### Biểu đồ tài nguyên

![So sánh CPU](results/busbooking_baseline/cpu_comparison.png)

![So sánh RAM](results/busbooking_baseline/ram_comparison.png)

## 5. Giới hạn và ý nghĩa của kết quả

- Đây là **baseline ở những mức tải đã thử**, không phải số người dùng tối đa mà hệ thống chịu được.
- Java, PostgreSQL và Locust cùng chạy trong một phiên Kaggle; số CPU/RAM chỉ phản ánh **tiến trình Java**. RAM chia 1024² nên là **MiB**, kể cả các PNG còn ghi nhãn MB. Mẫu CPU/RAM từng thời điểm không được lưu thành artifact, nên chỉ đối chiếu được summary/output notebook, chưa tính lại độc lập các giá trị tổng hợp.
- Mỗi mức tải đo một lần, thời lượng 60 giây có bao gồm ramp-up; chưa tách warm-up hoặc đo độ tin cậy thống kê qua nhiều lượt.
- Kịch bản POST sử dụng **một tài khoản USER test** dùng chung JWT cho các virtual user; không mô phỏng 20 tài khoản độc lập.
- POST phân phối trên nhiều Trip nhằm hạn chế hết ghế; không phải stress test dành riêng cho tranh chấp ghế cuối cùng.
- Không sử dụng POST fixture đã tiêu thụ ghế để chạy lại benchmark; cần tạo fixture mới hoặc khôi phục dữ liệu sạch.

**Kết luận:** CSV và console của GET/POST chính thức đều ghi **0 lỗi Locust**; output notebook/booking manifest ghi 0 Trip sai cân bằng ghế trong fixture sau tải. Bộ số liệu là **baseline Pha 1 tham khảo**; trước khi chốt so sánh Pha 2 cần làm rõ mốc CSV–console, phiên bản psutil, nguồn commit và giới hạn bằng chứng tài nguyên nêu trên.


