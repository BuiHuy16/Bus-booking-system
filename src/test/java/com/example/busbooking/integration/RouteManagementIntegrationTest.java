package com.example.busbooking.integration;

import com.example.busbooking.security.JwtService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RouteManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    String suffix;
    Long admin;
    Long user;

    @BeforeEach void seedUsers() {
        suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        admin = insertUser("ADMIN", "admin");
        user = insertUser("USER", "user");
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM trips WHERE route_id IN (SELECT id FROM routes WHERE origin_id IN (SELECT id FROM locations WHERE address LIKE ?))", "%" + suffix + "%");
        jdbc.update("DELETE FROM routes WHERE origin_id IN (SELECT id FROM locations WHERE address LIKE ?) OR destination_id IN (SELECT id FROM locations WHERE address LIKE ?)", "%" + suffix + "%", "%" + suffix + "%");
        jdbc.update("DELETE FROM vehicles WHERE license_plate LIKE ?", "%" + suffix.toUpperCase() + "%");
        jdbc.update("DELETE FROM bus_operators WHERE email LIKE ?", "%" + suffix + "%");
        jdbc.update("DELETE FROM locations WHERE address LIKE ?", "%" + suffix + "%");
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", admin, user);
    }

    @Test void adminCreatesActiveRouteAndFiltersFullLocationResponse() throws Exception {
        Long origin = insertLocation("Origin", "Hà Nội");
        Long destination = insertLocation("Destination", "Hải Phòng");
        String locationHeader = mvc.perform(post("/api/admin/routes").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(createBody(origin, destination, 120)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin.id").value(origin))
                .andExpect(jsonPath("$.origin.province").value("Hà Nội"))
                .andExpect(jsonPath("$.destination.id").value(destination))
                .andExpect(jsonPath("$.destination.address").value("Address " + suffix))
                .andExpect(jsonPath("$.estimatedDurationMinutes").value(120))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getHeader("Location");
        assertThat(locationHeader).startsWith("/api/admin/routes/");

        mvc.perform(get("/api/admin/routes").header("Authorization", token(admin))
                        .param("originId", origin.toString()).param("destinationId", destination.toString())
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].origin.id").value(origin));
        mvc.perform(get("/api/admin/routes").header("Authorization", token(admin))
                        .param("status", "INACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test void orderedPairIsUniqueButReverseDirectionIsAllowed() throws Exception {
        Long first = insertLocation("First", "A");
        Long second = insertLocation("Second", "B");
        mvc.perform(post("/api/admin/routes").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(createBody(first, second, 60)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/routes").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(createBody(first, second, 90)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ROUTE_ALREADY_EXISTS"));
        mvc.perform(post("/api/admin/routes").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(createBody(second, first, 60)))
                .andExpect(status().isCreated());
    }

    @Test void unreferencedRouteCanReplaceEndpointsAndReturnsFreshAssociations() throws Exception {
        Long origin = insertLocation("Origin", "A");
        Long destination = insertLocation("Destination", "B");
        Long replacement = insertLocation("Replacement", "C");
        Long route = insertRoute(origin, destination, "ACTIVE");

        mvc.perform(put("/api/admin/routes/" + route).header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(updateBody(replacement, destination, 150, "ACTIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin.id").value(replacement))
                .andExpect(jsonPath("$.origin.name").value("Replacement " + suffix))
                .andExpect(jsonPath("$.destination.id").value(destination))
                .andExpect(jsonPath("$.estimatedDurationMinutes").value(150));
        assertThat(jdbc.queryForObject("SELECT origin_id FROM routes WHERE id = ?", Long.class, route))
                .isEqualTo(replacement);
    }

    @Test void referencedEndpointsAreLockedAndOperationalTripBlocksDeactivation() throws Exception {
        Long origin = insertLocation("Origin", "A");
        Long destination = insertLocation("Destination", "B");
        Long replacement = insertLocation("Replacement", "C");
        Long route = insertRoute(origin, destination, "ACTIVE");
        Long vehicle = insertVehicle();
        insertTrip(route, vehicle, "COMPLETED");

        mvc.perform(put("/api/admin/routes/" + route).header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(updateBody(replacement, destination, 100, "ACTIVE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ROUTE_ENDPOINTS_LOCKED"));
        mvc.perform(put("/api/admin/routes/" + route).header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(updateBody(origin, destination, 100, "INACTIVE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
        mvc.perform(put("/api/admin/routes/" + route).header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(updateBody(origin, destination, 100, "ACTIVE")))
                .andExpect(status().isOk());
        insertTrip(route, vehicle, "SCHEDULED");
        mvc.perform(put("/api/admin/routes/" + route).header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(updateBody(origin, destination, 100, "INACTIVE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ROUTE_IN_USE"));
    }

    @Test void validatesStrictContractAndAuthorization() throws Exception {
        Long origin = insertLocation("Origin", "A");
        Long destination = insertLocation("Destination", "B");
        mvc.perform(get("/api/admin/routes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/routes").header("Authorization", token(user))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/routes").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(createBody(origin, origin, 60)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/routes").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON)
                        .content(createBody(origin, destination, 60).replace("}", ",\"status\":\"ACTIVE\"}")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/routes").header("Authorization", token(admin)).param("originId", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test void concurrentDuplicateCreatesAllowExactlyOneRoute() throws Exception {
        Long origin = insertLocation("Origin", "A");
        Long destination = insertLocation("Destination", "B");
        String request = createBody(origin, destination, 60);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> create = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Concurrent create timed out");
                return mvc.perform(post("/api/admin/routes").header("Authorization", token(admin))
                                .contentType(APPLICATION_JSON).content(request))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> first = pool.submit(create);
            Future<Integer> second = pool.submit(create);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(new Integer[]{first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM routes WHERE origin_id = ? AND destination_id = ?",
                    Integer.class, origin, destination)).isOne();
        } finally {
            pool.shutdownNow();
        }
    }

    private Long insertUser(String role, String prefix) {
        return jdbc.queryForObject("""
                INSERT INTO users(full_name,email,password,phone,role_id,status,email_verified)
                VALUES ('Test', ?, 'test-hash', '0901234567',
                        (SELECT id FROM roles WHERE name = ?), 'ACTIVE', true) RETURNING id
                """, Long.class, prefix + suffix + "@route-test.test", role);
    }

    private Long insertLocation(String name, String province) {
        return jdbc.queryForObject("INSERT INTO locations(name,province,address) VALUES (?, ?, ?) RETURNING id",
                Long.class, name + " " + suffix, province, "Address " + suffix);
    }

    private Long insertRoute(Long origin, Long destination, String status) {
        return jdbc.queryForObject("INSERT INTO routes(origin_id,destination_id,estimated_duration_minutes,status) VALUES (?, ?, 90, ?) RETURNING id",
                Long.class, origin, destination, status);
    }

    private Long insertVehicle() {
        Long operator = jdbc.queryForObject("""
                INSERT INTO bus_operators(name,address,phone,email,status)
                VALUES (?, 'Address', '0901234567', ?, 'ACTIVE') RETURNING id
                """, Long.class, "Operator " + suffix, "operator" + suffix + "@route-test.test");
        return jdbc.queryForObject("""
                INSERT INTO vehicles(operator_id,license_plate,vehicle_type,seat_capacity,status)
                VALUES (?, ?, 'SLEEPER', 40, 'ACTIVE') RETURNING id
                """, Long.class, operator, "51A" + suffix.toUpperCase());
    }

    private void insertTrip(Long route, Long vehicle, String status) {
        jdbc.update("""
                INSERT INTO trips(route_id,vehicle_id,departure_time,arrival_time,price,available_seats,status)
                VALUES (?, ?, now(), now() + interval '2 hour', 100000, 40, ?)
                """, route, vehicle, status);
    }

    private String token(Long userId) { return "Bearer " + jwt.issue(userId).value(); }

    private String createBody(Long origin, Long destination, int duration) {
        return """
                {"originId":%d,"destinationId":%d,"estimatedDurationMinutes":%d}
                """.formatted(origin, destination, duration);
    }

    private String updateBody(Long origin, Long destination, int duration, String status) {
        return """
                {"originId":%d,"destinationId":%d,"estimatedDurationMinutes":%d,"status":"%s"}
                """.formatted(origin, destination, duration, status);
    }
}
