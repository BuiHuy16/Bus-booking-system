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
class LocationManagementIntegrationTest {
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
        jdbc.update("DELETE FROM routes WHERE origin_id IN (SELECT id FROM locations WHERE name LIKE ?) OR destination_id IN (SELECT id FROM locations WHERE name LIKE ?)",
                "%" + suffix + "%", "%" + suffix + "%");
        jdbc.update("DELETE FROM locations WHERE name LIKE ? OR address LIKE ?", "%" + suffix + "%", "%" + suffix + "%");
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", admin, user);
    }

    @Test void publicListSearchesCaseInsensitivelyAndReturnsFullResponse() throws Exception {
        jdbc.update("INSERT INTO locations(name,province,address) VALUES (?, ?, ?)",
                "Bến xe " + suffix, "Hà Nội", "Đường " + suffix);

        mvc.perform(get("/api/locations").param("q", "  hÀ   nỘi  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Bến xe " + suffix))
                .andExpect(jsonPath("$[0].province").value("Hà Nội"))
                .andExpect(jsonPath("$[0].address").value("Đường " + suffix))
                .andExpect(jsonPath("$[0].id").isNumber());
        mvc.perform(get("/api/locations").param("q", " "))
                .andExpect(status().isOk());
        mvc.perform(get("/api/locations").param("q", "x".repeat(101)))
                .andExpect(status().isBadRequest());
    }

    @Test void adminCanCreateAndDuplicateVariantsReturn409() throws Exception {
        String body = body("  Bến   xe " + suffix + "  ", "  Hà   Nội ", " Đường  " + suffix);
        String expectedLocation = mvc.perform(post("/api/admin/locations").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Bến xe " + suffix))
                .andExpect(jsonPath("$.province").value("Hà Nội"))
                .andExpect(jsonPath("$.address").value("Đường " + suffix))
                .andReturn().getResponse().getHeader("Location");
        assertThat(expectedLocation).startsWith("/api/admin/locations/");

        mvc.perform(post("/api/admin/locations").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON)
                        .content(body(("BẾN XE " + suffix).toUpperCase(), "hà nội", "đường " + suffix)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("LOCATION_ALREADY_EXISTS"));
    }

    @Test void updateIsFullReplacementAndAllowsReferencedLocation() throws Exception {
        Long origin = insertLocation("Origin " + suffix);
        Long destination = insertLocation("Destination " + suffix);
        jdbc.update("INSERT INTO routes(origin_id,destination_id,estimated_duration_minutes,status) VALUES (?, ?, 60, 'ACTIVE')",
                origin, destination);

        mvc.perform(put("/api/admin/locations/" + origin).header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(body("Updated " + suffix, "Province", "Address")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated " + suffix));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM routes WHERE origin_id = ?", Integer.class, origin)).isOne();

        mvc.perform(put("/api/admin/locations/" + origin).header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Only name\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/locations/999999999").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(body("Missing", "Province", "Address")))
                .andExpect(status().isNotFound());
    }

    @Test void adminEndpointsEnforceRolesAndStrictJson() throws Exception {
        mvc.perform(get("/api/admin/locations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/locations").header("Authorization", token(user))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/locations").header("Authorization", token(admin))).andExpect(status().isOk());
        mvc.perform(post("/api/admin/locations").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON)
                        .content(body("Strict " + suffix, "Province", "Address").replace("}", ",\"status\":\"ACTIVE\"}")))
                .andExpect(status().isBadRequest());
    }

    @Test void concurrentDuplicateCreatesAllowExactlyOneLocation() throws Exception {
        String request = body("Concurrent " + suffix, "Province", "Address " + suffix);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> create = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Concurrent create timed out");
                return mvc.perform(post("/api/admin/locations").header("Authorization", token(admin))
                                .contentType(APPLICATION_JSON).content(request))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> first = pool.submit(create);
            Future<Integer> second = pool.submit(create);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(new Integer[]{first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM locations
                    WHERE lower(name) = lower(?) AND lower(province) = lower(?) AND lower(address) = lower(?)
                    """, Integer.class, "Concurrent " + suffix, "Province", "Address " + suffix)).isOne();
        } finally {
            pool.shutdownNow();
        }
    }

    private Long insertUser(String role, String prefix) {
        return jdbc.queryForObject("""
                INSERT INTO users(full_name,email,password,phone,role_id,status,email_verified)
                VALUES ('Test', ?, 'test-hash', '0901234567',
                        (SELECT id FROM roles WHERE name = ?), 'ACTIVE', true) RETURNING id
                """, Long.class, prefix + suffix + "@location-test.test", role);
    }

    private Long insertLocation(String name) {
        return jdbc.queryForObject("INSERT INTO locations(name,province,address) VALUES (?, 'Test', ?) RETURNING id",
                Long.class, name, "Address " + suffix);
    }

    private String token(Long userId) {
        return "Bearer " + jwt.issue(userId).value();
    }

    private String body(String name, String province, String address) {
        return """
                {"name":"%s","province":"%s","address":"%s"}
                """.formatted(name, province, address);
    }
}
