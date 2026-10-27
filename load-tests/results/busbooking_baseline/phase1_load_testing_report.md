# BusBooking — Phase 1 Load Testing Report

**Environment:** Kaggle CPU  
**Generated:** 2026-10-27 15:48 UTC  
**Tools:** Locust, Spring Boot, PostgreSQL

## 1. Test objectives

Establish a performance baseline for Phase 2.

Workloads:
- GET /api/trips and GET /api/trips/{id}
- POST /api/bookings

## 2. Test configuration

- GET: 1, 5, 10, 20 virtual users
- POST: 20 virtual users
- Duration: 60 seconds per level
- Spawn rate: up to 5 users/second
- Wait time: 0.2–0.6 seconds
- Load generator and backend share the Kaggle CPU environment
- CPU/RAM statistics measure the Java process only

## 3. Results at 20 virtual users

| Workload | Users | Requests | RPS | Avg ms | p95 ms | Errors | Java CPU | Java RAM |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| GET Trip | 20 | 2834 | 48.01 | 5.09 | 6.00 | 0.00% | 19.44% | 543.86 MB |
| POST Booking | 20 | 2789 | 47.21 | 16.09 | 25.00 | 0.00% | 83.02% | 643.30 MB |

## 4. Database consistency

Trips with incorrect seat balance: **0**

No seat balance inconsistency was detected in the
tested POST Booking fixture.

## 5. Interpretation

Both final workloads completed with zero recorded
Locust request failures.

POST Booking includes authentication, transaction
processing and database writes. Its workload is
different from GET Trip, so their results should
not be interpreted as a direct architecture comparison.

These measurements are an initial baseline, not the
maximum capacity of the system.

## 6. Limitations

- All components share the Kaggle CPU environment.
- Measurements are affected by JVM warm-up and caching.
- Workload uses one authenticated test account for POST.
- CPU/RAM exclude PostgreSQL and Locust processes.
- Hardware and fixture must be kept comparable in Phase 2.
- The 60-second measurements include the ramp-up period.
- POST benchmark distributes bookings across many Trips;
  it is not a dedicated last-seat contention test.

## 7. Phase 2 comparison requirements

Use the same:
- CPU environment and recorded hardware configuration
- Database fixture and data volume
- Locust scripts, concurrency, spawn rate and duration
- JVM and PostgreSQL settings, or document differences
- Resource measurement method

Compare RPS, p95 latency, error rate, CPU and RAM.
