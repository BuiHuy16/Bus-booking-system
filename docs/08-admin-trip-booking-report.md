# Admin Trip & Booking implementation report

Verified 24 October 2026, Asia/Saigon. Existing layered architecture retained; no new dependencies or migrations.

## A. API implementation

| Endpoint | Implemented | Success HTTP status | Authorization |
| --- | --- | --- | --- |
| GET /api/admin/trips | Yes | 200 | ADMIN, SUPER_ADMIN |
| POST /api/admin/trips | Yes | 201 + Location | ADMIN, SUPER_ADMIN |
| PUT /api/admin/trips/{tripId} | Yes | 200 | ADMIN, SUPER_ADMIN |
| DELETE /api/admin/trips/{tripId} | Yes; soft cancellation | 200, including repeat | ADMIN, SUPER_ADMIN |
| GET /api/admin/bookings | Yes | 200 | ADMIN, SUPER_ADMIN |

Anonymous and invalid JWT requests return 401; USER requests return 403.
Missing resources return 404, invalid input 400, and trip state/capacity/confirmed-booking conflicts 409 TRIP_CONFLICT.
No extra admin endpoints were added. Existing public and customer API shapes remain unchanged.

## B. Files changed

Java paths below are relative to `src/main/java/com/example/busbooking/`, except explicit `src/test` and `docs` paths.
B = read/create, C = update, D = cancellation, E = booking listing, F = final verification/documentation.

| File | New/Modified | Phase | Reason |
| --- | --- | --- | --- |
| api/controller/AdminTripController.java | New | B–D | Four admin trip operations; existing mapper and bearer scheme |
| api/controller/AdminBookingController.java | New | E | Filtered booking listing across users |
| api/controller/BookingController.java | Modified | E | Recognize SUPER_ADMIN for existing detail/cancellation |
| api/dto/trip/TripRequest.java | Modified placeholder | B | Validated complete request; reject unknown fields |
| business/command/SaveTripCommand.java | New | B | Request data passed to business layer without API DTO dependency |
| business/service/TripService.java | Modified | B–D | Validation, creation, locked updates and cancellation |
| business/service/BookingService.java | Modified | E | Read-only admin listing; existing cancellation reused |
| business/port/repository/TripRepository.java | Modified | B | Admin filtering and trip persistence port methods |
| business/port/repository/BookingRepository.java | Modified | C, E | Confirmed-seat aggregate and admin filters |
| business/port/repository/RouteRepository.java | Modified placeholder | B | Route eligibility lookup port |
| business/port/repository/VehicleRepository.java | Modified placeholder | B | Vehicle eligibility/capacity lookup port |
| data/adapter/TripRepositoryAdapter.java | Modified | B | Persist details; refresh only changed trip; map filtered results |
| data/adapter/BookingRepositoryAdapter.java | Modified | C, E | Aggregate and filtered listing implementation |
| data/adapter/RouteRepositoryAdapter.java | Modified placeholder | B | Convert persisted route eligibility into domain data |
| data/adapter/VehicleRepositoryAdapter.java | Modified placeholder | B | Convert persisted vehicle eligibility/capacity into domain data |
| data/repository/SpringDataTripRepository.java | Modified | B, C | Admin query; root-row pessimistic lock avoids joined-query race |
| data/repository/SpringDataBookingRepository.java | Modified | C, E | Confirmed quantities SUM and optional booking filters |
| data/repository/SpringDataRouteRepository.java | Modified placeholder | B | Route JPA lookup |
| data/repository/SpringDataVehicleRepository.java | Modified placeholder | B | Vehicle JPA lookup |
| data/entity/TripEntity.java | Modified | B | Targeted persistence of trip details |
| data/entity/RouteEntity.java | Modified | B | Read existing status column |
| data/entity/VehicleEntity.java | Modified | B | Read existing status column |
| domain/model/Route.java | Modified placeholder | B | Domain route eligibility record |
| domain/model/Vehicle.java | Modified placeholder | B | Domain vehicle eligibility/capacity record |
| domain/enums/RouteStatus.java | Modified placeholder | B | Schema-defined ACTIVE/INACTIVE values |
| domain/enums/VehicleStatus.java | Modified placeholder | B | Schema-defined ACTIVE/INACTIVE/MAINTENANCE values |
| domain/exception/TripConflictException.java | New | C | Explicit trip business conflicts |
| exception/GlobalExceptionHandler.java | Modified | C | Map trip conflicts to sanitized 409 response |
| src/test/java/com/example/busbooking/business/AdminTripServiceTest.java | New | B–D | 17 service tests |
| src/test/java/com/example/busbooking/business/AdminBookingServiceTest.java | New | E | 2 service tests |
| src/test/java/com/example/busbooking/integration/AdminManagementIntegrationTest.java | New | B–F | 24 PostgreSQL/MockMvc tests, including 5 deterministic races |
| docs/04-api-design.md | Modified, preserving existing user edits | F | Update implementation status; contract 4.13 and interim policy |
| docs/08-admin-trip-booking-report.md | New | F | This report, file inventory and Swagger guide |

Total: 8 new files and 25 modified files. No files were deleted. Existing empty test placeholders remain untouched.
No pre-existing test files, test methods, assertions, Flyway migrations, constraints, security configuration, or build dependencies were changed.
No commit or push was performed.

## C. Test results

The baseline was run before API changes against the verified isolated PostgreSQL test database.

| Test category | Total | Passed | Failed | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| Pre-existing baseline/regression tests | 184 | 184 | 0 | 0 | 0 |
| New admin service unit tests | 19 | 19 | 0 | 0 | 0 |
| New HTTP/security/PostgreSQL tests, excluding concurrency | 19 | 19 | 0 | 0 | 0 |
| New deterministic PostgreSQL concurrency tests | 5 | 5 | 0 | 0 | 0 |
| **Final full Maven suite** | **227** | **227** | **0** | **0** | **0** |

**43 tests added. All 184 pre-existing tests remain present and unchanged. Full suite passed.**
HTTP/controller and persistence behavior are tested together through MockMvc with the real security chain and PostgreSQL, rather than substituting mocked persistence.

| Phase gate | Maven tests | Result |
| --- | ---: | --- |
| A: baseline | 184 | BUILD SUCCESS |
| B: read/create + existing trip service/adapter | 19 | BUILD SUCCESS |
| C: update + both vehicle/booking race orderings + existing trip/booking tests | 49 | BUILD SUCCESS |
| D: cancellation + final-seat and cancellation races + existing booking tests | 55 | BUILD SUCCESS |
| E: booking filters, roles, ownership, Swagger + targeted regressions | 69 | BUILD SUCCESS |
| F: targeted tests plus flushed rollback/persistence-context regressions | 72 | BUILD SUCCESS |
| F: `.\mvnw.cmd clean test` | 227 | BUILD SUCCESS; Maven exit code 0 |

Phase counts overlap and are not additive. Each gate passed before the next implementation phase.
Diff checks found no unrelated source changes. Full Maven default discovery was preserved, including the existing opt-in convention for live SMTP/HTTP `*IT` smoke tests; those are outside the standard `test` run.

Local build evidence is retained under `target/admin-management-verification/`, including
`baseline-test.log`, `phase-b-test.log`, `phase-c-test.log`, `phase-d-test.log`, `phase-e-test.log`,
`phase-f-targeted-test.log`, and `final-test.log`. Surefire XML remains in `target/surefire-reports/`.
These generated files are ignored by Git and will be removed by a future Maven clean.

## D. Bugs found and fixed

1. **SUPER_ADMIN ownership bypass missing.** SecurityConfig already allowed this role on booking detail/cancellation,
   but BookingController only recognized ADMIN. Its existing role check now accepts SUPER_ADMIN too.
   A PostgreSQL HTTP regression verifies both admin roles can view/cancel another user's booking while a different USER receives 403.

2. **False trip-not-found after a concurrent vehicle swap.** The previous pessimistic query joined vehicle/operator before locking.
   The deterministic booking-after-update test reproduced a ResourceNotFoundException after the waiting query resumed,
   because its join depended on the vehicle foreign key changed by the committing transaction.
   SpringDataTripRepository now locks only the trip root row; associations load after the lock is acquired.
   Both update/booking orderings now pass and preserve database seat invariants.

3. **Read-only JPA associations can retain the old foreign-key target.** TripEntity owns route_id/vehicle_id as scalar fields;
   its route/vehicle associations are mapped insertable=false/updatable=false. Merely changing the scalar can leave
   the managed association referring to the previous vehicle. TripRepositoryAdapter saves and flushes first, then refreshes
   only that trip. Tests verify the returned replacement vehicle capacity, correct subsequent booking cancellation,
   and preservation of other managed entities and pending changes. No persistence-context clear was introduced.

4. **Unknown trip request fields initially ignored during development.** A new HTTP test caught that ordinary
   ignoreUnknown=false metadata did not enforce strict handling with the configured Jackson defaults.
   TripRequest now uses the existing auth DTO JsonAnySetter rejection pattern. availableSeats/status and other unsupported fields return 400.

An incremental verification run also loaded an invalid generated controller class despite correct source imports;
a clean rebuild resolved it. The final successful run was a complete clean build.

## E. Database and concurrency safety

- Verified `busbooking-test-db`, host port **55433**, database/user **busbooking_test**, PostgreSQL **16.15**.
  The pre-existing test container was started without recreating its data. Test contexts use the existing `test` profile
  and TEST_DB_URL/USERNAME/PASSWORD configuration; no new passwords were embedded in source or commands.
  No destructive operation or integration test targeted the development database/container on port 5435.
- Trip creation/update/cancellation use service-level transactions. Listing is read-only.
  Existing booking transaction boundaries, atomic seat-delta SQL, status checks and ownership validation remain intact.
- Update and cancellation acquire the same PESSIMISTIC_WRITE trip lock used by booking creation and cancellation.
  Confirmed-seat totals are queried **after** obtaining the lock. No booking-row locks are added to the aggregate query,
  so it does not invert existing booking-cancellation lock ordering.
- Creation uses full capacity and SCHEDULED. Same-vehicle edits preserve current availability.
  Vehicle changes use `new capacity - SUM(CONFIRMED quantity)`; cancelled bookings do not consume capacity.
  Too-small replacements cause 409. Historical booking unit prices and totals are preserved.
- Trip cancellation never deletes a row and never changes customer bookings. It rejects confirmed bookings and
  started/completed trips. Repeated cancellation is an idempotent 200. Cancelled booking history stays attached.
- The five concurrency tests use separate threads, transactions and connections. Latches hold one transaction open;
  pg_stat_activity confirms the competing connection is waiting on a real database lock before commit is released.
  No sleeps determine ordering. Tests cover two distinct users competing for the final seat, both vehicle-change/booking
  orderings, and both trip-cancellation/booking orderings. Assertions check persisted status, quantity sums and available seats.
- Admin trip lists fetch route, locations, vehicle and operator together. BookingResponse maps scalar booking fields,
  without loading user entities. List queries have stable ordering and no per-result association lookups.

Compatibility decisions: public trip search currently includes all states; that behavior remains unchanged.
Admin listing adds route/status filtering. Departure validation follows existing schema constraints (arrival after departure),
without inventing a future-only rule. The old API design deferred cancellation; the task's explicit interim policy is now
documented in contract 4.13. Route/Vehicle ports, adapters and enums were empty placeholders, so only the required lookup behavior was filled in.

## F. Swagger testing guide

Run the application with its existing development configuration. Open
`http://localhost:8085/swagger-ui/index.html`, log in with an active verified ADMIN/SUPER_ADMIN account,
and paste its JWT into **Authorize → bearerAuth**. Use existing route/vehicle IDs from your environment.
The examples do not require resetting development data.

1. **List trips:** `GET /api/admin/trips?routeId=1&status=SCHEDULED&departureDate=2026-11-10`.
   Expect 200 with TripResponse array; remove any/all filters to inspect other states.
2. **Create a trip:** `POST /api/admin/trips` with the JSON below.
   Expect 201, Location `/api/trips/{id}`, SCHEDULED, and availableSeats equal to vehicle capacity.
3. **Update the returned trip:** `PUT /api/admin/trips/{id}` with the same complete JSON, changing price or vehicleId as needed.
   Expect 200 and preserved booked seats. A vehicle with capacity below confirmed quantities returns 409.
4. **Cancel it:** `DELETE /api/admin/trips/{id}`. Expect 200/CANCELLED when there are no confirmed bookings;
   repeat for the same response semantics. A trip with confirmed bookings returns 409 without changing them.
5. **List bookings:** `GET /api/admin/bookings?userId=1&tripId=1&status=CONFIRMED`.
   Expect 200 with BookingResponse array; filters are optional and empty matches return `[]`.

```json
{
  "routeId": 1,
  "vehicleId": 1,
  "departureTime": "2026-11-10T08:00:00",
  "arrivalTime": "2026-11-10T11:00:00",
  "price": 150000
}
```

For manual security checks, repeat with USER JWT (403), no JWT (401), and malformed JWT (401).
For booking detail/cancellation, use the existing `/api/bookings/{bookingId}` endpoints with either admin role.
Do not use a second admin booking cancellation/status endpoint.

## G. Remaining work

All five requested endpoints and regression coverage are implemented. No known implementation blocker remains.

Deferred business decisions: final cancellation/refund rules, mass cancellation, customer notifications,
and whether admins may change route/times/prices after confirmed bookings under stricter future policies.
Current scheduled-trip edits preserve booking records and prices as required.

Lists are unpaginated for Phase 1. Direct SQL writers or future route/vehicle capacity/status management must
coordinate with the same trip-lock/seat rules; this change does not add those management endpoints.
Vehicle schedule overlap constraints and a future-only departure rule are not established in the current schema/contract.
The other 14 planned admin operations remain outside this task.
