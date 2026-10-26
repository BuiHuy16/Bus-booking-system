package com.example.busbooking.integration;

import com.example.busbooking.business.service.*;
import com.example.busbooking.security.JwtService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import com.example.busbooking.business.command.SaveTripCommand;
import com.example.busbooking.domain.exception.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;
    @Autowired BookingService bookings;
    @Autowired TripService trips;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired jakarta.persistence.EntityManager entityManager;
    Long user, otherUser, admin, superAdmin, origin, destination, route, operator, vehicle;
    String suffix;

    @BeforeEach void seed() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("busbooking_test");
        suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        user = insertUser("USER", "user");
        otherUser = insertUser("USER", "other");
        admin = insertUser("ADMIN", "admin");
        superAdmin = insertUser("SUPER_ADMIN", "super");
        origin = jdbc.queryForObject("INSERT INTO locations(name,province,address) VALUES (?, 'Test', 'Test') RETURNING id", Long.class, "Origin " + suffix);
        destination = jdbc.queryForObject("INSERT INTO locations(name,province,address) VALUES (?, 'Test', 'Test') RETURNING id", Long.class, "Destination " + suffix);
        route = jdbc.queryForObject("INSERT INTO routes(origin_id,destination_id,estimated_duration_minutes,status) VALUES (?, ?, 120, 'ACTIVE') RETURNING id", Long.class, origin, destination);
        operator = jdbc.queryForObject("INSERT INTO bus_operators(name,address,phone,email,status) VALUES (?, 'Test', '0901234567', ?, 'ACTIVE') RETURNING id", Long.class, "Operator " + suffix, suffix + "@operator.test");
        vehicle = insertVehicle(10);
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM bookings WHERE trip_id IN (SELECT id FROM trips WHERE route_id = ?)", route);
        jdbc.update("DELETE FROM trips WHERE route_id = ?", route);
        jdbc.update("DELETE FROM vehicles WHERE operator_id = ?", operator);
        jdbc.update("DELETE FROM bus_operators WHERE id = ?", operator);
        jdbc.update("DELETE FROM routes WHERE id = ?", route);
        jdbc.update("DELETE FROM locations WHERE id IN (?, ?)", origin, destination);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?, ?)", user, otherUser, admin, superAdmin);
    }

    Long insertUser(String role, String name) {
        return jdbc.queryForObject("""
            INSERT INTO users(full_name,email,password,phone,role_id,status,email_verified)
            VALUES ('Test', ?, 'test-hash', '0901234567', (SELECT id FROM roles WHERE name = ?), 'ACTIVE', true) RETURNING id
            """, Long.class, name + suffix + "@admin-test.test", role);
    }

    Long insertVehicle(int capacity) {
        return jdbc.queryForObject("""
            INSERT INTO vehicles(operator_id,license_plate,vehicle_type,seat_capacity,status)
            VALUES (?, ?, 'SLEEPER', ?, 'ACTIVE') RETURNING id
            """, Long.class, operator, UUID.randomUUID().toString().substring(0, 18).toUpperCase(), capacity);
    }

    String token(Long id) { return "Bearer " + jwt.issue(id).value(); }

    String body(Long vehicleId) {
        return """
            {"routeId":%d,"vehicleId":%d,"departureTime":"2026-11-10T08:00:00",
             "arrivalTime":"2026-11-10T10:00:00","price":150000}
            """.formatted(route, vehicleId);
    }

    Long createTrip() throws Exception {
        String result = mvc.perform(post("/api/admin/trips").header("Authorization", token(admin))
                .contentType(APPLICATION_JSON).content(body(vehicle))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("id").asLong();
    }

    @Test void createPersistsCapacityStatusAndDetailedResponse() throws Exception {
        Long id = createTrip();
        assertThat(jdbc.queryForObject("SELECT available_seats FROM trips WHERE id = ?", Integer.class, id)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT status FROM trips WHERE id = ?", String.class, id)).isEqualTo("SCHEDULED");
        mvc.perform(get("/api/admin/trips").header("Authorization", token(superAdmin)).param("routeId", route.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].route.origin").value("Origin " + suffix))
                .andExpect(jsonPath("$[0].vehicle.seatCapacity").value(10));
    }

    @Test void listIncludesAllStatesAndCombinesOptionalFilters() throws Exception {
        Long id = createTrip();
        jdbc.update("UPDATE trips SET status = 'COMPLETED' WHERE id = ?", id);
        createTrip();
        mvc.perform(get("/api/admin/trips").header("Authorization", token(admin)).param("routeId", route.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/admin/trips").header("Authorization", token(admin)).param("routeId", route.toString())
                .param("status", "COMPLETED").param("departureDate", "2026-11-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/admin/trips").header("Authorization", token(admin)).param("routeId", route.toString())
                .param("departureDate", "2026-11-11")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/admin/trips").header("Authorization", token(admin)))
                .andExpect(status().isOk());
    }

    @Test void readAndCreateRequireAdminRoleAndValidJwt() throws Exception {
        for (var request : List.of(get("/api/admin/trips"), post("/api/admin/trips").contentType(APPLICATION_JSON).content(body(vehicle)))) {
            mvc.perform(request).andExpect(status().isUnauthorized());
            mvc.perform(request.header("Authorization", token(user))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/trips").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/trips").header("Authorization", token(superAdmin))
                .contentType(APPLICATION_JSON).content(body(vehicle))).andExpect(status().isCreated());
    }

    @Test void invalidCreateAndFiltersAre400AndMissingReferencesAre404() throws Exception {
        for (String invalid : List.of("{}", body(vehicle).replace("150000", "-1"),
                body(vehicle).replace("150000", "1.001"), body(vehicle).replace("10:00:00", "08:00:00"),
                body(vehicle).replace("150000", "10000000000"))) {
            mvc.perform(post("/api/admin/trips").header("Authorization", token(admin))
                    .contentType(APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/admin/trips").header("Authorization", token(admin))
                .contentType(APPLICATION_JSON).content(body(999999999L))).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/trips").header("Authorization", token(admin)).param("status", "INVALID"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/trips").header("Authorization", token(admin)).param("departureDate", "2026-02-30"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trips WHERE route_id = ?", Integer.class, route)).isZero();
    }

    @Test void createCannotSetSeatCountOrStatusAndAcceptsZeroPrice() throws Exception {
        mvc.perform(post("/api/admin/trips").header("Authorization", token(admin)).contentType(APPLICATION_JSON)
                .content(body(vehicle).replace("150000", "0").replace("}", ",\"availableSeats\":999,\"status\":\"COMPLETED\"}")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/trips").header("Authorization", token(admin)).contentType(APPLICATION_JSON)
                .content(body(vehicle).replace("150000", "0"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.availableSeats").value(10)).andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test void updateVehicleKeepsConfirmedSeatsAndReturnsFreshVehicleMapping() throws Exception {
        Long id = createTrip();
        var confirmed = bookings.create(user, id, "Passenger", "0901234567", 3);
        var cancelled = bookings.create(otherUser, id, "Other", "0901234567", 2);
        bookings.cancel(otherUser, false, cancelled.getId());
        Long replacement = insertVehicle(5);
        mvc.perform(put("/api/admin/trips/" + id).header("Authorization", token(superAdmin))
                .contentType(APPLICATION_JSON).content(body(replacement).replace("150000", "200000")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableSeats").value(2))
                .andExpect(jsonPath("$.vehicleId").value(replacement))
                .andExpect(jsonPath("$.vehicle.seatCapacity").value(5));
        assertThat(jdbc.queryForObject("SELECT vehicle_id FROM trips WHERE id = ?", Long.class, id)).isEqualTo(replacement);
        assertThat(jdbc.queryForObject("SELECT available_seats FROM trips WHERE id = ?", Integer.class, id)).isEqualTo(2);
        assertThat(bookings.get(user, false, confirmed.getId()).getUnitPrice()).isEqualByComparingTo("150000");
        bookings.cancel(user, false, confirmed.getId());
        assertThat(jdbc.queryForObject("SELECT available_seats FROM trips WHERE id = ?", Integer.class, id)).isEqualTo(5);
    }

    @Test void updatingSameVehiclePreservesReservedSeatsAndBookingPrices() throws Exception {
        Long id = createTrip();
        bookings.create(user, id, "Passenger", "0901234567", 4);
        mvc.perform(put("/api/admin/trips/" + id).header("Authorization", token(admin))
                .contentType(APPLICATION_JSON).content(body(vehicle).replace("150000", "200000")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableSeats").value(6));
        assertThat(jdbc.queryForObject("SELECT available_seats FROM trips WHERE id = ?", Integer.class, id)).isEqualTo(6);
    }

    @Test void insufficientCapacityRollsBackTripAndPreservesBooking() throws Exception {
        Long id = createTrip();
        var booking = bookings.create(user, id, "Passenger", "0901234567", 4);
        Long replacement = insertVehicle(3);
        mvc.perform(put("/api/admin/trips/" + id).header("Authorization", token(admin))
                .contentType(APPLICATION_JSON).content(body(replacement).replace("150000", "200000")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("TRIP_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT vehicle_id FROM trips WHERE id = ?", Long.class, id)).isEqualTo(vehicle);
        assertThat(jdbc.queryForObject("SELECT available_seats FROM trips WHERE id = ?", Integer.class, id)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT price FROM trips WHERE id = ?", java.math.BigDecimal.class, id)).isEqualByComparingTo("150000");
        assertThat(bookings.get(user, false, booking.getId()).getStatus().name()).isEqualTo("CONFIRMED");
    }

    @Test void updateChecksAuthMissingInvalidReferencesAndTripState() throws Exception {
        Long id = createTrip();
        mvc.perform(put("/api/admin/trips/" + id).contentType(APPLICATION_JSON).content(body(vehicle)))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/trips/" + id).header("Authorization", token(user))
                .contentType(APPLICATION_JSON).content(body(vehicle))).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/trips/999999999").header("Authorization", token(admin))
                .contentType(APPLICATION_JSON).content(body(vehicle))).andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/trips/" + id).header("Authorization", token(admin))
                .contentType(APPLICATION_JSON).content(body(999999999L))).andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/trips/" + id).header("Authorization", token(admin))
                .contentType(APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        for (String state : List.of("IN_PROGRESS", "COMPLETED", "CANCELLED")) {
            jdbc.update("UPDATE trips SET status = ? WHERE id = ?", state, id);
            mvc.perform(put("/api/admin/trips/" + id).header("Authorization", token(admin))
                    .contentType(APPLICATION_JSON).content(body(vehicle))).andExpect(status().isConflict());
        }
    }

    SaveTripCommand command(Long vehicleId) {
        return new SaveTripCommand(route, vehicleId, LocalDateTime.of(2026, 11, 10, 8, 0),
                LocalDateTime.of(2026, 11, 10, 10, 0), new BigDecimal("150000"));
    }

    // Hold a real transaction open, then observe the competing PostgreSQL connection waiting on
    // its row lock before allowing commit. No sleep-based assumptions about thread scheduling.
    Throwable compete(Runnable holder, Supplier<?> contender) throws Exception {
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        String applicationName = "admin-race-" + suffix;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                holder.run();
                held.countDown();
                try {
                    if (!release.await(15, TimeUnit.SECONDS)) throw new AssertionError("Holder timed out");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
            }));
            assertThat(held.await(10, TimeUnit.SECONDS)).as("Holder acquired lock").isTrue();
            Future<Throwable> second = pool.submit(() -> {
                try {
                    new TransactionTemplate(transactionManager).execute(tx -> {
                        jdbc.queryForObject("SELECT set_config('application_name', ?, true)", String.class, applicationName);
                        return contender.get();
                    });
                    return null;
                } catch (RuntimeException error) {
                    return error;
                }
            });
            boolean waiting = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline && !second.isDone()) {
                waiting = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM pg_stat_activity
                    WHERE application_name = ? AND wait_event_type = 'Lock')
                    """, Boolean.class, applicationName));
                if (waiting) break;
                Thread.onSpinWait();
            }
            assertThat(waiting).as("Contender waits on the database lock").isTrue();
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            return second.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    void assertSeatInvariant(Long tripId, int capacity, int confirmedSeats) {
        assertThat(jdbc.queryForObject("SELECT available_seats FROM trips WHERE id = ?", Integer.class, tripId))
                .isEqualTo(capacity - confirmedSeats);
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(quantity), 0) FROM bookings WHERE trip_id = ? AND status = 'CONFIRMED'", Long.class, tripId))
                .isEqualTo(confirmedSeats);
    }

    @Test @Tag("concurrency") void bookingWaitsForVehicleChangeAndCannotOversellNewCapacity() throws Exception {
        Long id = createTrip();
        bookings.create(user, id, "First", "0901234567", 3);
        Long replacement = insertVehicle(5);
        Throwable failure = compete(() -> trips.update(id, command(replacement)),
                () -> bookings.create(otherUser, id, "Other", "0901234567", 3));
        assertThat(failure).isInstanceOf(InsufficientSeatsException.class);
        assertThat(jdbc.queryForObject("SELECT vehicle_id FROM trips WHERE id = ?", Long.class, id)).isEqualTo(replacement);
        assertSeatInvariant(id, 5, 3);
    }

    @Test @Tag("concurrency") void vehicleChangeWaitsForBookingThenRejectsInsufficientCapacity() throws Exception {
        Long id = createTrip();
        Long replacement = insertVehicle(3);
        Throwable failure = compete(() -> bookings.create(user, id, "First", "0901234567", 4),
                () -> trips.update(id, command(replacement)));
        assertThat(failure).isInstanceOf(TripConflictException.class);
        assertThat(jdbc.queryForObject("SELECT vehicle_id FROM trips WHERE id = ?", Long.class, id)).isEqualTo(vehicle);
        assertSeatInvariant(id, 10, 4);
    }

    @Test void softCancelIsIdempotentAndKeepsCancelledBookingHistory() throws Exception {
        Long id = createTrip();
        var cancelledBooking = bookings.create(user, id, "Passenger", "0901234567", 2);
        bookings.cancel(user, false, cancelledBooking.getId());
        for (Long actor : List.of(admin, superAdmin)) {
            mvc.perform(delete("/api/admin/trips/" + id).header("Authorization", token(actor)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trips WHERE id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, cancelledBooking.getId())).isEqualTo("CANCELLED");
        assertSeatInvariant(id, 10, 0);
    }

    @Test void confirmedBookingPreventsCancellationWithoutChangingAnything() throws Exception {
        Long id = createTrip();
        var confirmed = bookings.create(user, id, "Passenger", "0901234567", 2);
        mvc.perform(delete("/api/admin/trips/" + id).header("Authorization", token(admin)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("TRIP_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT status FROM trips WHERE id = ?", String.class, id)).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, confirmed.getId())).isEqualTo("CONFIRMED");
        assertSeatInvariant(id, 10, 2);
    }

    @Test void cancelChecksAuthMissingIdAndNonScheduledStates() throws Exception {
        Long id = createTrip();
        mvc.perform(delete("/api/admin/trips/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/trips/" + id).header("Authorization", token(user))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/trips/999999999").header("Authorization", token(admin))).andExpect(status().isNotFound());
        for (String state : List.of("IN_PROGRESS", "COMPLETED")) {
            jdbc.update("UPDATE trips SET status = ? WHERE id = ?", state, id);
            mvc.perform(delete("/api/admin/trips/" + id).header("Authorization", token(admin))).andExpect(status().isConflict());
        }
    }

    @Test @Tag("concurrency") void bookingWaitsForCancellationAndCannotBookCancelledTrip() throws Exception {
        Long id = createTrip();
        Throwable failure = compete(() -> trips.cancel(id),
                () -> bookings.create(user, id, "Passenger", "0901234567", 1));
        assertThat(failure).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM trips WHERE id = ?", String.class, id)).isEqualTo("CANCELLED");
        assertSeatInvariant(id, 10, 0);
    }

    @Test @Tag("concurrency") void cancellationWaitsForBookingAndThenRejectsConfirmedBooking() throws Exception {
        Long id = createTrip();
        Throwable failure = compete(() -> bookings.create(user, id, "Passenger", "0901234567", 1),
                () -> trips.cancel(id));
        assertThat(failure).isInstanceOf(TripConflictException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM trips WHERE id = ?", String.class, id)).isEqualTo("SCHEDULED");
        assertSeatInvariant(id, 10, 1);
    }

    @Test @Tag("concurrency") void twoDifferentUsersCannotBookTheFinalSeat() throws Exception {
        Long id = createTrip();
        bookings.create(user, id, "Existing", "0901234567", 9);
        Throwable failure = compete(() -> bookings.create(user, id, "First", "0901234567", 1),
                () -> bookings.create(otherUser, id, "Second", "0901234567", 1));
        assertThat(failure).isInstanceOf(InsufficientSeatsException.class);
        assertSeatInvariant(id, 10, 10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings WHERE trip_id = ? AND user_id = ?", Integer.class, id, otherUser)).isZero();
    }

    @Test void adminBookingListIncludesMultipleUsersAndCombinesFiltersWithoutSecrets() throws Exception {
        Long id = createTrip();
        var first = bookings.create(user, id, "First", "0901234567", 2);
        var second = bookings.create(otherUser, id, "Second", "0901234567", 1);
        bookings.cancel(otherUser, false, second.getId());
        for (Long actor : List.of(admin, superAdmin)) {
            mvc.perform(get("/api/admin/bookings").header("Authorization", token(actor)).param("tripId", id.toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].password").doesNotExist())
                    .andExpect(jsonPath("$[0].email").doesNotExist())
                    .andExpect(jsonPath("$[0].user").doesNotExist());
        }
        mvc.perform(get("/api/admin/bookings").header("Authorization", token(admin)).param("userId", user.toString())
                .param("tripId", id.toString()).param("status", "CONFIRMED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(first.getId()));
        mvc.perform(get("/api/admin/bookings").header("Authorization", token(admin)).param("status", "CANCELLED")
                .param("userId", otherUser.toString())).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(second.getId()));
        mvc.perform(get("/api/admin/bookings").header("Authorization", token(admin)).param("userId", user.toString())
                .param("status", "CANCELLED")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/admin/bookings").header("Authorization", token(superAdmin))).andExpect(status().isOk());
    }

    @Test void bookingListRequiresAdminAndRejectsInvalidFilters() throws Exception {
        mvc.perform(get("/api/admin/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/bookings").header("Authorization", token(user))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/bookings").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/bookings").header("Authorization", token(admin)).param("status", "INVALID"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/bookings").header("Authorization", token(admin)).param("userId", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test void bothAdminRolesUseExistingBookingDetailAndCancellationWhileUserOwnershipRemainsEnforced() throws Exception {
        Long id = createTrip();
        for (Long actor : List.of(admin, superAdmin)) {
            var booking = bookings.create(user, id, "Passenger", "0901234567", 1);
            mvc.perform(get("/api/bookings/" + booking.getId()).header("Authorization", token(otherUser)))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/bookings/" + booking.getId()).header("Authorization", token(otherUser)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/bookings/" + booking.getId()).header("Authorization", token(actor)))
                    .andExpect(status().isOk());
            mvc.perform(delete("/api/bookings/" + booking.getId()).header("Authorization", token(actor)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        }
        assertSeatInvariant(id, 10, 0);
    }

    @Test void swaggerHasExactlyFiveAdminOperationsAndUsesExistingBearerScheme() throws Exception {
        var document = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        var paths = document.get("paths");
        for (String path : List.of("/api/admin/trips", "/api/admin/trips/{tripId}", "/api/admin/bookings")) {
            var item = paths.get(path);
            assertThat(item).isNotNull();
            item.properties().forEach(entry -> assertThat(entry.getValue().get("security").toString()).contains("bearerAuth"));
        }
        assertThat(paths.get("/api/admin/trips").size()).isEqualTo(2);
        assertThat(paths.get("/api/admin/trips").get("post").get("responses").has("201")).isTrue();
        assertThat(paths.get("/api/admin/trips/{tripId}").size()).isEqualTo(2);
        assertThat(paths.get("/api/admin/bookings").size()).isEqualTo(1);
        assertThat(paths.has("/api/admin/bookings/{bookingId}")).isFalse();
        assertThat(paths.has("/api/admin/bookings/{bookingId}/status")).isFalse();
        assertThat(document.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
    }

    @Test void flushedTripUpdateRollsBackVehicleSeatsAndPriceWhenTransactionFails() throws Exception {
        Long id = createTrip();
        bookings.create(user, id, "Passenger", "0901234567", 3);
        Long replacement = insertVehicle(5);
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            trips.update(id, command(replacement));
            throw new IllegalStateException("Force transaction rollback after trip flush");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT vehicle_id FROM trips WHERE id = ?", Long.class, id)).isEqualTo(vehicle);
        assertSeatInvariant(id, 10, 3);
    }

    @Test void refreshingChangedTripPreservesOtherManagedEntitiesAndTheirPendingChanges() throws Exception {
        Long id = createTrip();
        var booking = bookings.create(user, id, "Passenger", "0901234567", 3);
        Long replacement = insertVehicle(5);
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            var managedBooking = entityManager.find(com.example.busbooking.data.entity.BookingEntity.class, booking.getId());
            managedBooking.setPassengerName("Updated passenger");
            var updated = trips.update(id, command(replacement));
            assertThat(updated.getVehicle().seatCapacity()).isEqualTo(5);
            assertThat(updated.getAvailableSeats()).isEqualTo(2);
            assertThat(entityManager.contains(managedBooking)).isTrue();
            managedBooking.setPassengerPhone("0907654321");
        });
        assertThat(jdbc.queryForObject("SELECT passenger_name FROM bookings WHERE id = ?", String.class, booking.getId()))
                .isEqualTo("Updated passenger");
        assertThat(jdbc.queryForObject("SELECT passenger_phone FROM bookings WHERE id = ?", String.class, booking.getId()))
                .isEqualTo("0907654321");
        assertSeatInvariant(id, 5, 3);
    }

    @Test void inactiveRouteAndMaintenanceVehicleAreRejectedForCreateAndUpdate() throws Exception {
        Long id = createTrip();
        jdbc.update("UPDATE routes SET status = 'INACTIVE' WHERE id = ?", route);
        for (var request : List.of(post("/api/admin/trips"), put("/api/admin/trips/" + id))) {
            mvc.perform(request.header("Authorization", token(admin)).contentType(APPLICATION_JSON).content(body(vehicle)))
                    .andExpect(status().isBadRequest());
        }
        jdbc.update("UPDATE routes SET status = 'ACTIVE' WHERE id = ?", route);
        jdbc.update("UPDATE vehicles SET status = 'MAINTENANCE' WHERE id = ?", vehicle);
        for (var request : List.of(post("/api/admin/trips"), put("/api/admin/trips/" + id))) {
            mvc.perform(request.header("Authorization", token(admin)).contentType(APPLICATION_JSON).content(body(vehicle)))
                    .andExpect(status().isBadRequest());
        }
        assertSeatInvariant(id, 10, 0);
    }
}
