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
class OperatorManagementIntegrationTest {
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
        jdbc.update("DELETE FROM trips WHERE vehicle_id IN (SELECT id FROM vehicles WHERE operator_id IN (SELECT id FROM bus_operators WHERE name LIKE ?))", "%" + suffix + "%");
        jdbc.update("DELETE FROM vehicles WHERE operator_id IN (SELECT id FROM bus_operators WHERE name LIKE ?)", "%" + suffix + "%");
        jdbc.update("DELETE FROM bus_operators WHERE name LIKE ? OR email LIKE ?", "%" + suffix + "%", "%" + suffix + "%");
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", admin, user);
    }

    @Test void adminCreatesActiveNormalizedOperatorAndListsWithFilters() throws Exception {
        mvc.perform(post("/api/admin/operators").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON)
                        .content(createBody("  Nhà   xe " + suffix + "  ", " CONTACT." + suffix + "@EXAMPLE.COM ")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/admin/operators/[0-9]+")))
                .andExpect(jsonPath("$.name").value("Nhà xe " + suffix))
                .andExpect(jsonPath("$.phone").value("0901234567"))
                .andExpect(jsonPath("$.email").value("contact." + suffix + "@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mvc.perform(get("/api/admin/operators").header("Authorization", token(admin))
                        .param("q", "  NHÀ   XE " + suffix).param("status", "ACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Nhà xe " + suffix));
        mvc.perform(get("/api/admin/operators").header("Authorization", token(admin))
                        .param("status", "INACTIVE"))
                .andExpect(status().isOk());
    }

    @Test void duplicatesAndConcurrentCreatesReturnConflict() throws Exception {
        mvc.perform(post("/api/admin/operators").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(createBody("Operator " + suffix, suffix + "@example.com")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/operators").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(createBody(("OPERATOR " + suffix).toUpperCase(), "other." + suffix + "@example.com")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("OPERATOR_ALREADY_EXISTS"));

        String concurrentName = "Concurrent " + suffix;
        String request = createBody(concurrentName, "concurrent." + suffix + "@example.com");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> create = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Concurrent create timed out");
                return mvc.perform(post("/api/admin/operators").header("Authorization", token(admin))
                                .contentType(APPLICATION_JSON).content(request))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> first = pool.submit(create);
            Future<Integer> second = pool.submit(create);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(new Integer[]{first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void deactivationIsBlockedByOperationalVehicleOrTripWithoutCascade() throws Exception {
        Long operator = insertOperator("Used " + suffix, "used." + suffix + "@example.com");
        Long vehicle = jdbc.queryForObject("""
                INSERT INTO vehicles(operator_id,license_plate,vehicle_type,seat_capacity,status)
                VALUES (?, ?, 'SLEEPER', 20, 'ACTIVE') RETURNING id
                """, Long.class, operator, suffix.toUpperCase());

        mvc.perform(put("/api/admin/operators/" + operator).header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(updateBody("Used " + suffix,
                                "used." + suffix + "@example.com", "INACTIVE")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("OPERATOR_IN_USE"));
        assertThat(jdbc.queryForObject("SELECT status FROM bus_operators WHERE id = ?", String.class, operator)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT status FROM vehicles WHERE id = ?", String.class, vehicle)).isEqualTo("ACTIVE");

        jdbc.update("UPDATE vehicles SET status = 'INACTIVE' WHERE id = ?", vehicle);
        Long origin = insertLocation("Origin " + suffix);
        Long destination = insertLocation("Destination " + suffix);
        Long route = jdbc.queryForObject("INSERT INTO routes(origin_id,destination_id,estimated_duration_minutes,status) VALUES (?, ?, 60, 'ACTIVE') RETURNING id",
                Long.class, origin, destination);
        jdbc.update("INSERT INTO trips(route_id,vehicle_id,departure_time,arrival_time,price,available_seats,status) VALUES (?, ?, now(), now() + interval '1 hour', 100000, 20, 'SCHEDULED')",
                route, vehicle);
        mvc.perform(put("/api/admin/operators/" + operator).header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(updateBody("Used " + suffix,
                                "used." + suffix + "@example.com", "INACTIVE")))
                .andExpect(status().isConflict());
        jdbc.update("DELETE FROM trips WHERE route_id = ?", route);
        jdbc.update("DELETE FROM routes WHERE id = ?", route);
        jdbc.update("DELETE FROM locations WHERE id IN (?, ?)", origin, destination);

        mvc.perform(put("/api/admin/operators/" + operator).header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(updateBody("Renamed " + suffix,
                                "used." + suffix + "@example.com", "INACTIVE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test void updateIsStrictFullReplacementAndSecurityIsEnforced() throws Exception {
        Long operator = insertOperator("Strict " + suffix, "strict." + suffix + "@example.com");
        mvc.perform(get("/api/admin/operators")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/operators").header("Authorization", token(user))).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/operators/" + operator).header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Only name\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/operators").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON)
                        .content(createBody("Invalid " + suffix, "invalid." + suffix + "@example.com")
                                .replace("}", ",\"status\":\"INACTIVE\"}")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/operators/999999999").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(updateBody("Missing", "missing." + suffix + "@example.com", "ACTIVE")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/operators").header("Authorization", token(admin)).param("status", "INVALID"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/operators").header("Authorization", token(admin)).param("q", "x".repeat(101)))
                .andExpect(status().isBadRequest());
    }

    private Long insertUser(String role, String prefix) {
        return jdbc.queryForObject("""
                INSERT INTO users(full_name,email,password,phone,role_id,status,email_verified)
                VALUES ('Test', ?, 'test-hash', '0901234567',
                        (SELECT id FROM roles WHERE name = ?), 'ACTIVE', true) RETURNING id
                """, Long.class, prefix + suffix + "@operator-test.test", role);
    }

    private Long insertOperator(String name, String email) {
        return jdbc.queryForObject("""
                INSERT INTO bus_operators(name,address,phone,email,status)
                VALUES (?, 'Address', '0901234567', ?, 'ACTIVE') RETURNING id
                """, Long.class, name, email);
    }

    private Long insertLocation(String name) {
        return jdbc.queryForObject("INSERT INTO locations(name,province,address) VALUES (?, 'Test', ?) RETURNING id",
                Long.class, name, "Address " + suffix);
    }

    private String token(Long userId) { return "Bearer " + jwt.issue(userId).value(); }

    private String createBody(String name, String email) {
        return """
                {"name":"%s","address":"  Main   office  ","phone":"+84901234567","email":"%s"}
                """.formatted(name, email);
    }

    private String updateBody(String name, String email, String status) {
        return """
                {"name":"%s","address":"Main office","phone":"0901234567","email":"%s","status":"%s"}
                """.formatted(name, email, status);
    }
}
