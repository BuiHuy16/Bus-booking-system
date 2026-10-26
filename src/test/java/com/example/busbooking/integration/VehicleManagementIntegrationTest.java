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
class VehicleManagementIntegrationTest {
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
        jdbc.update("DELETE FROM trips WHERE vehicle_id IN (SELECT id FROM vehicles WHERE license_plate LIKE ?)", "%" + suffix.toUpperCase() + "%");
        jdbc.update("DELETE FROM routes WHERE origin_id IN (SELECT id FROM locations WHERE address LIKE ?)", "%" + suffix + "%");
        jdbc.update("DELETE FROM locations WHERE address LIKE ?", "%" + suffix + "%");
        jdbc.update("DELETE FROM vehicles WHERE license_plate LIKE ?", "%" + suffix.toUpperCase() + "%");
        jdbc.update("DELETE FROM bus_operators WHERE email LIKE ?", "%" + suffix + "%");
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", admin, user);
    }

    @Test void adminCreatesNormalizedActiveVehicleAndFiltersResponse() throws Exception {
        Long operator = insertOperator("Alpha", "ACTIVE");
        String header = mvc.perform(post("/api/admin/vehicles").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON)
                        .content(createBody(operator, "51a " + suffix, "  Giường   nằm  ", 40)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operator.id").value(operator))
                .andExpect(jsonPath("$.operator.name").value("Alpha " + suffix))
                .andExpect(jsonPath("$.licensePlate").value("51A" + suffix.toUpperCase()))
                .andExpect(jsonPath("$.vehicleType").value("Giường nằm"))
                .andExpect(jsonPath("$.seatCapacity").value(40))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getHeader("Location");
        assertThat(header).startsWith("/api/admin/vehicles/");

        mvc.perform(get("/api/admin/vehicles").header("Authorization", token(admin))
                        .param("q", "giường").param("operatorId", operator.toString()).param("status", "ACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].licensePlate").value("51A" + suffix.toUpperCase()));
        mvc.perform(get("/api/admin/vehicles").header("Authorization", token(admin))
                        .param("q", "51a " + suffix))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].licensePlate").value("51A" + suffix.toUpperCase()));
        mvc.perform(get("/api/admin/vehicles").header("Authorization", token(admin)).param("status", "INACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test void normalizedLicensePlateIsUniqueAndConcurrentSafe() throws Exception {
        Long operator = insertOperator("Unique", "ACTIVE");
        String firstBody = createBody(operator, "52a " + suffix, "Bus", 30);
        mvc.perform(post("/api/admin/vehicles").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(firstBody))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/vehicles").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON)
                        .content(createBody(operator, ("52A" + suffix).toUpperCase(), "Other", 40)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("VEHICLE_ALREADY_EXISTS"));
    }

    @Test void unreferencedVehicleCanMoveOperatorAndReturnsFreshAssociation() throws Exception {
        Long first = insertOperator("First", "ACTIVE");
        Long second = insertOperator("Second", "ACTIVE");
        Long vehicle = insertVehicle(first, "53A" + suffix.toUpperCase(), "Bus", 30, "ACTIVE");

        mvc.perform(put("/api/admin/vehicles/" + vehicle).header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON)
                        .content(updateBody(second, "53A" + suffix.toUpperCase(), "Limousine", 35, "ACTIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operator.id").value(second))
                .andExpect(jsonPath("$.operator.name").value("Second " + suffix))
                .andExpect(jsonPath("$.vehicleType").value("Limousine"))
                .andExpect(jsonPath("$.seatCapacity").value(35));
    }

    @Test void referencedIdentityIsLockedAndOperationalTripBlocksCapacityAndStatus() throws Exception {
        Long operator = insertOperator("Referenced", "ACTIVE");
        Long vehicle = insertVehicle(operator, "54A" + suffix.toUpperCase(), "Bus", 30, "ACTIVE");
        Long route = insertRoute();
        insertTrip(route, vehicle, "SCHEDULED");

        mvc.perform(put("/api/admin/vehicles/" + vehicle).header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON)
                        .content(updateBody(operator, "54B" + suffix.toUpperCase(), "Bus", 30, "ACTIVE")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("VEHICLE_IDENTITY_LOCKED"));
        mvc.perform(put("/api/admin/vehicles/" + vehicle).header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON)
                        .content(updateBody(operator, "54A" + suffix.toUpperCase(), "Bus", 40, "ACTIVE")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("VEHICLE_IN_USE"));
        mvc.perform(put("/api/admin/vehicles/" + vehicle).header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON)
                        .content(updateBody(operator, "54A" + suffix.toUpperCase(), "Bus", 30, "MAINTENANCE")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("VEHICLE_IN_USE"));
    }

    @Test void historicalTripsAllowCapacityAndLifecycleChanges() throws Exception {
        Long operator = insertOperator("Historical", "ACTIVE");
        Long vehicle = insertVehicle(operator, "55A" + suffix.toUpperCase(), "Bus", 30, "ACTIVE");
        insertTrip(insertRoute(), vehicle, "COMPLETED");
        mvc.perform(put("/api/admin/vehicles/" + vehicle).header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON)
                        .content(updateBody(operator, "55A" + suffix.toUpperCase(), "Bus", 40, "MAINTENANCE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatCapacity").value(40))
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));
    }

    @Test void inactiveOperatorAndStrictAuthorizationAreEnforced() throws Exception {
        Long inactive = insertOperator("Inactive", "INACTIVE");
        mvc.perform(post("/api/admin/vehicles").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON).content(createBody(inactive, "56A" + suffix, "Bus", 30)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("OPERATOR_INACTIVE"));
        mvc.perform(get("/api/admin/vehicles")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/vehicles").header("Authorization", token(user))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/vehicles").header("Authorization", token(admin)).param("operatorId", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/vehicles").header("Authorization", token(admin))
                        .contentType(APPLICATION_JSON)
                        .content(createBody(inactive, "56B" + suffix, "Bus", 30).replace("}", ",\"status\":\"ACTIVE\"}")))
                .andExpect(status().isBadRequest());
    }

    @Test void concurrentDuplicateCreatesAllowExactlyOneVehicle() throws Exception {
        Long operator = insertOperator("Concurrent", "ACTIVE");
        String request = createBody(operator, "57a " + suffix, "Bus", 30);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> create = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Concurrent create timed out");
                return mvc.perform(post("/api/admin/vehicles").header("Authorization", token(admin))
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

    private Long insertUser(String role, String prefix) {
        return jdbc.queryForObject("""
                INSERT INTO users(full_name,email,password,phone,role_id,status,email_verified)
                VALUES ('Test', ?, 'test-hash', '0901234567',
                        (SELECT id FROM roles WHERE name = ?), 'ACTIVE', true) RETURNING id
                """, Long.class, prefix + suffix + "@vehicle-test.test", role);
    }

    private Long insertOperator(String name, String status) {
        return jdbc.queryForObject("""
                INSERT INTO bus_operators(name,address,phone,email,status)
                VALUES (?, 'Address', '0901234567', ?, ?) RETURNING id
                """, Long.class, name + " " + suffix, name.toLowerCase() + suffix + "@vehicle-test.test", status);
    }

    private Long insertVehicle(Long operator, String plate, String type, int capacity, String status) {
        return jdbc.queryForObject("""
                INSERT INTO vehicles(operator_id,license_plate,vehicle_type,seat_capacity,status)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, operator, plate, type, capacity, status);
    }

    private Long insertRoute() {
        Long origin = jdbc.queryForObject("INSERT INTO locations(name,province,address) VALUES (?, 'Test', ?) RETURNING id",
                Long.class, "Origin " + UUID.randomUUID(), "Address " + suffix);
        Long destination = jdbc.queryForObject("INSERT INTO locations(name,province,address) VALUES (?, 'Test', ?) RETURNING id",
                Long.class, "Destination " + UUID.randomUUID(), "Address " + suffix);
        return jdbc.queryForObject("INSERT INTO routes(origin_id,destination_id,estimated_duration_minutes,status) VALUES (?, ?, 60, 'ACTIVE') RETURNING id",
                Long.class, origin, destination);
    }

    private void insertTrip(Long route, Long vehicle, String status) {
        jdbc.update("""
                INSERT INTO trips(route_id,vehicle_id,departure_time,arrival_time,price,available_seats,status)
                VALUES (?, ?, now(), now() + interval '1 hour', 100000, 30, ?)
                """, route, vehicle, status);
    }

    private String token(Long userId) { return "Bearer " + jwt.issue(userId).value(); }

    private String createBody(Long operator, String plate, String type, int capacity) {
        return """
                {"operatorId":%d,"licensePlate":"%s","vehicleType":"%s","seatCapacity":%d}
                """.formatted(operator, plate, type, capacity);
    }

    private String updateBody(Long operator, String plate, String type, int capacity, String status) {
        return """
                {"operatorId":%d,"licensePlate":"%s","vehicleType":"%s","seatCapacity":%d,"status":"%s"}
                """.formatted(operator, plate, type, capacity, status);
    }
}
