package com.example.busbooking.integration;

import com.example.busbooking.api.dto.booking.BookingResponse;
import com.example.busbooking.api.dto.trip.TripResponse;
import com.example.busbooking.business.service.BookingService;
import com.example.busbooking.security.JwtService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TripBookingIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired BookingService bookingService;
    @Autowired ObjectMapper json;

    private final String fixture = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private Long userId;
    private Long otherUserId;
    private Long adminId;
    private Long originId;
    private Long destinationId;
    private Long routeId;
    private Long vehicleId;
    private Long tripId;

    @BeforeEach
    void seed() {
        String suffix = fixture + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        Long userRole = roleId("USER");
        Long adminRole = roleId("ADMIN");
        userId = insertUser("user_" + suffix + "@example.com", userRole);
        otherUserId = insertUser("other_" + suffix + "@example.com", userRole);
        adminId = insertUser("admin_" + suffix + "@example.com", adminRole);
        originId = insertLocation("Origin " + suffix);
        destinationId = insertLocation("Destination " + suffix);
        routeId = jdbc.queryForObject("""
                INSERT INTO routes (origin_id, destination_id, estimated_duration_minutes, status)
                VALUES (?, ?, 120, 'ACTIVE') RETURNING id
                """, Long.class, originId, destinationId);
        Long operatorId = jdbc.queryForObject("""
                INSERT INTO bus_operators (name, address, phone, email, status)
                VALUES (?, 'Test address', '0900000000', ?, 'ACTIVE') RETURNING id
                """, Long.class, "Operator " + suffix, "operator_" + suffix + "@example.com");
        vehicleId = jdbc.queryForObject("""
                INSERT INTO vehicles (operator_id, license_plate, vehicle_type, seat_capacity, status)
                VALUES (?, ?, 'SLEEPER', 10, 'ACTIVE') RETURNING id
                """, Long.class, operatorId, ("T-" + suffix.substring(0, Math.min(17, suffix.length()))).toUpperCase());
        tripId = insertTrip(LocalDateTime.of(2026, 10, 25, 8, 0), 4, "SCHEDULED");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM bookings WHERE user_id IN (?, ?, ?)", userId, otherUserId, adminId);
        jdbc.update("DELETE FROM trips WHERE route_id = ?", routeId);
        jdbc.update("DELETE FROM vehicles WHERE id = ?", vehicleId);
        jdbc.update("DELETE FROM bus_operators WHERE email LIKE ?", "operator_" + fixture + "%");
        jdbc.update("DELETE FROM routes WHERE id = ?", routeId);
        jdbc.update("DELETE FROM locations WHERE id IN (?, ?)", originId, destinationId);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?)", userId, otherUserId, adminId);
    }

    @Test
    @Order(1)
    void tripSearchSupportsFiltersWholeDateAndDetailedResponse() throws Exception {
        insertTrip(LocalDateTime.of(2026, 10, 25, 23, 59), 3, "SCHEDULED");
        insertTrip(LocalDateTime.of(2026, 10, 26, 0, 0), 2, "SCHEDULED");

        MvcResult result = mvc.perform(get("/api/trips")
                        .param("originId", originId.toString())
                        .param("destinationId", destinationId.toString())
                        .param("departureDate", "2026-10-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].departureTime").value("2026-10-25T08:00:00"))
                .andExpect(jsonPath("$[1].departureTime").value("2026-10-25T23:59:00"))
                .andExpect(jsonPath("$[0].route.origin").value(org.hamcrest.Matchers.startsWith("Origin " + fixture)))
                .andExpect(jsonPath("$[0].route.destination").value(org.hamcrest.Matchers.startsWith("Destination " + fixture)))
                .andExpect(jsonPath("$[0].vehicle.vehicleType").value("SLEEPER"))
                .andExpect(jsonPath("$[0].vehicle.operatorName").value(org.hamcrest.Matchers.startsWith("Operator " + fixture)))
                .andReturn();

        TripResponse[] trips = json.readValue(result.getResponse().getContentAsString(), TripResponse[].class);
        assertThat(trips[0].routeId()).isEqualTo(routeId);
        assertThat(trips[0].vehicleId()).isEqualTo(vehicleId);
    }

    @Test
    @Order(2)
    void tripSearchAndGetHandleEmptyInvalidAndMissingRequests() throws Exception {
        mvc.perform(get("/api/trips").param("originId", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
        mvc.perform(get("/api/trips").param("departureDate", "2026-02-30"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/trips").param("originId", "999999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/trips/" + (tripId + 999999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @Order(3)
    void bookingLifecyclePersistsValuesProtectsOwnershipAndRestoresSeats() throws Exception {
        String token = jwt.issue(userId).value();
        String body = """
                {"tripId":%d,"passengerName":"Nguyen Van A","passengerPhone":"0901234567","quantity":2}
                """.formatted(tripId);

        MvcResult created = mvc.perform(post("/api/bookings").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tripId").value(tripId))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.unitPrice").value(150000.00))
                .andExpect(jsonPath("$.totalAmount").value(300000.00))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.bookingCode").value(org.hamcrest.Matchers.hasLength(23)))
                .andReturn();
        BookingResponse booking = json.readValue(created.getResponse().getContentAsString(), BookingResponse.class);
        assertThat(jdbc.queryForObject("SELECT available_seats FROM trips WHERE id = ?", Integer.class, tripId)).isEqualTo(2);

        mvc.perform(get("/api/bookings/" + booking.id()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(booking.id()));
        mvc.perform(get("/api/bookings/" + booking.id()).header("Authorization", "Bearer " + jwt.issue(otherUserId).value()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings/" + booking.id()).header("Authorization", "Bearer " + jwt.issue(adminId).value()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me/bookings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(booking.id()));
        mvc.perform(delete("/api/bookings/" + booking.id()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("SELECT available_seats FROM trips WHERE id = ?", Integer.class, tripId)).isEqualTo(4);
        mvc.perform(delete("/api/bookings/" + booking.id()).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("BUSINESS_ERROR"));
    }

    @Test
    @Order(4)
    void bookingRejectsMissingInvalidAuthValidationAndInsufficientSeats() throws Exception {
        String body = """
                {"tripId":%d,"passengerName":"A","passengerPhone":"0901234567","quantity":1}
                """.formatted(tripId);
        mvc.perform(post("/api/bookings").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/bookings").header("Authorization", "Bearer invalid")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/bookings").header("Authorization", "Bearer " + jwt.issue(userId).value())
                        .contentType(APPLICATION_JSON).content(body.replace("\"quantity\":1", "\"quantity\":0")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        String tooMany = body.replace("\"quantity\":1", "\"quantity\":5");
        mvc.perform(post("/api/bookings").header("Authorization", "Bearer " + jwt.issue(userId).value())
                        .contentType(APPLICATION_JSON).content(tooMany))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("INSUFFICIENT_SEATS"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings WHERE trip_id = ?", Long.class, tripId)).isZero();
        assertThat(jdbc.queryForObject("SELECT available_seats FROM trips WHERE id = ?", Integer.class, tripId)).isEqualTo(4);
    }

    @Test
    @Order(5)
    void concurrentRequestsCannotOversellSeats() throws Exception {
        jdbc.update("UPDATE trips SET available_seats = 1 WHERE id = ?", tripId);
        String body = """
                {"tripId":%d,"passengerName":"Concurrent","passengerPhone":"0901234567","quantity":1}
                """.formatted(tripId);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> request = () -> mvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + jwt.issue(userId).value())
                            .contentType(APPLICATION_JSON).content(body))
                    .andReturn().getResponse().getStatus();
            Future<Integer> first = pool.submit(request);
            Future<Integer> second = pool.submit(request);
            int firstStatus = first.get(20, TimeUnit.SECONDS);
            int secondStatus = second.get(20, TimeUnit.SECONDS);
            assertNotEquals(firstStatus, secondStatus);
            assertThat(java.util.List.of(firstStatus, secondStatus))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("SELECT available_seats FROM trips WHERE id = ?", Integer.class, tripId)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings WHERE trip_id = ?", Long.class, tripId)).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Order(6)
    void bookingSaveFailureRollsBackSeatReservation() {
        assertThatThrownBy(() -> bookingService.create(
                userId, tripId, " ", "0901234567", 1))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT available_seats FROM trips WHERE id = ?", Integer.class, tripId))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings WHERE trip_id = ?", Long.class, tripId))
                .isZero();
    }

    private Long roleId(String name) {
        return jdbc.queryForObject("SELECT id FROM roles WHERE name = ?", Long.class, name);
    }

    private Long insertUser(String email, Long roleId) {
        return jdbc.queryForObject("""
                INSERT INTO users (full_name, email, password, phone, role_id, status, email_verified)
                VALUES (?, ?, 'test-hash', '0900000000', ?, 'ACTIVE', TRUE) RETURNING id
                """, Long.class, email, email, roleId);
    }

    private Long insertLocation(String name) {
        return jdbc.queryForObject("""
                INSERT INTO locations (name, province, address)
                VALUES (?, 'Test Province', ?) RETURNING id
                """, Long.class, name, name + " address");
    }

    private Long insertTrip(LocalDateTime departure, int seats, String status) {
        return jdbc.queryForObject("""
                INSERT INTO trips (route_id, vehicle_id, departure_time, arrival_time, price, available_seats, status)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, routeId, vehicleId, departure, departure.plusHours(2),
                new BigDecimal("150000.00"), seats, status);
    }
}
