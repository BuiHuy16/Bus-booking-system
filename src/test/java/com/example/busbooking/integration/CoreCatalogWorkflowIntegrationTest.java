package com.example.busbooking.integration;

import com.example.busbooking.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoreCatalogWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;

    String suffix;
    Long adminId;
    Long userId;
    Long originId;
    Long destinationId;
    Long operatorId;
    Long vehicleId;
    Long routeId;
    Long tripId;

    @BeforeEach void setUp() {
        suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        adminId = insertUser("ADMIN", "admin");
        userId = insertUser("USER", "user");
    }

    @AfterEach void cleanup() {
        if (tripId != null) jdbc.update("DELETE FROM trips WHERE id = ?", tripId);
        if (routeId != null) jdbc.update("DELETE FROM routes WHERE id = ?", routeId);
        if (vehicleId != null) jdbc.update("DELETE FROM vehicles WHERE id = ?", vehicleId);
        if (operatorId != null) jdbc.update("DELETE FROM bus_operators WHERE id = ?", operatorId);
        if (originId != null) jdbc.update("DELETE FROM locations WHERE id = ?", originId);
        if (destinationId != null) jdbc.update("DELETE FROM locations WHERE id = ?", destinationId);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", adminId, userId);
    }

    @Test void completeFourModuleWorkflowHonorsNormalizationRelationsAndLifecycle() throws Exception {
        originId = id(mvc.perform(post("/api/admin/locations").header("Authorization", token(adminId))
                        .contentType(APPLICATION_JSON)
                        .content(locationBody("  Bến   đi " + suffix, " Hà   Nội ", " Đường  A " + suffix)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Bến đi " + suffix))
                .andReturn().getResponse().getContentAsString());
        destinationId = id(mvc.perform(post("/api/admin/locations").header("Authorization", token(adminId))
                        .contentType(APPLICATION_JSON)
                        .content(locationBody("Bến đến " + suffix, "Hải Phòng", "Đường B " + suffix)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        operatorId = id(mvc.perform(post("/api/admin/operators").header("Authorization", token(adminId))
                        .contentType(APPLICATION_JSON).content(operatorCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("0901234567"))
                .andExpect(jsonPath("$.email").value("contact." + suffix + "@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString());

        vehicleId = id(mvc.perform(post("/api/admin/vehicles").header("Authorization", token(adminId))
                        .contentType(APPLICATION_JSON).content(vehicleCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licensePlate").value("51A" + suffix.toUpperCase()))
                .andExpect(jsonPath("$.vehicleType").value("Giường nằm"))
                .andExpect(jsonPath("$.operator.id").value(operatorId))
                .andReturn().getResponse().getContentAsString());

        routeId = id(mvc.perform(post("/api/admin/routes").header("Authorization", token(adminId))
                        .contentType(APPLICATION_JSON).content(routeCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin.id").value(originId))
                .andExpect(jsonPath("$.destination.id").value(destinationId))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(get("/api/admin/locations").header("Authorization", token(adminId)).param("q", suffix))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/admin/operators").header("Authorization", token(adminId))
                        .param("q", suffix).param("status", "ACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(operatorId));
        mvc.perform(get("/api/admin/vehicles").header("Authorization", token(adminId))
                        .param("operatorId", operatorId.toString()).param("status", "ACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(vehicleId));
        mvc.perform(get("/api/admin/routes").header("Authorization", token(adminId))
                        .param("originId", originId.toString()).param("destinationId", destinationId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(routeId));

        assertDuplicateRequestsReturn409();
        tripId = jdbc.queryForObject("""
                INSERT INTO trips(route_id,vehicle_id,departure_time,arrival_time,price,available_seats,status)
                VALUES (?, ?, now(), now() + interval '2 hour', 100000, 40, 'SCHEDULED') RETURNING id
                """, Long.class, routeId, vehicleId);
        assertOperationalDependenciesReturn409();

        mvc.perform(put("/api/admin/locations/" + originId).header("Authorization", token(adminId))
                        .contentType(APPLICATION_JSON)
                        .content(locationBody("Bến đi mới " + suffix, "Hà Nội", "Đường A " + suffix)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Bến đi mới " + suffix));
        assertThat(jdbc.queryForObject("SELECT origin_id FROM routes WHERE id = ?", Long.class, routeId))
                .isEqualTo(originId);
    }

    @Test void allFourAdminModulesEnforceAuthenticationAndRole() throws Exception {
        for (String path : new String[]{"/api/admin/locations", "/api/admin/operators",
                "/api/admin/vehicles", "/api/admin/routes"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", token(userId))).andExpect(status().isForbidden());
            mvc.perform(get(path).header("Authorization", token(adminId))).andExpect(status().isOk());
        }
    }

    private void assertDuplicateRequestsReturn409() throws Exception {
        mvc.perform(post("/api/admin/locations").header("Authorization", token(adminId))
                        .contentType(APPLICATION_JSON)
                        .content(locationBody(("BẾN ĐI " + suffix).toUpperCase(), "hà nội", "đường a " + suffix)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("LOCATION_ALREADY_EXISTS"));
        mvc.perform(post("/api/admin/operators").header("Authorization", token(adminId))
                        .contentType(APPLICATION_JSON).content(operatorCreateBody()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("OPERATOR_ALREADY_EXISTS"));
        mvc.perform(post("/api/admin/vehicles").header("Authorization", token(adminId))
                        .contentType(APPLICATION_JSON).content(vehicleCreateBody()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("VEHICLE_ALREADY_EXISTS"));
        mvc.perform(post("/api/admin/routes").header("Authorization", token(adminId))
                        .contentType(APPLICATION_JSON).content(routeCreateBody()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ROUTE_ALREADY_EXISTS"));
    }

    private void assertOperationalDependenciesReturn409() throws Exception {
        mvc.perform(put("/api/admin/routes/" + routeId).header("Authorization", token(adminId))
                        .contentType(APPLICATION_JSON).content(routeUpdateBody("INACTIVE")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ROUTE_IN_USE"));
        mvc.perform(put("/api/admin/vehicles/" + vehicleId).header("Authorization", token(adminId))
                        .contentType(APPLICATION_JSON).content(vehicleUpdateBody("MAINTENANCE")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("VEHICLE_IN_USE"));
        mvc.perform(put("/api/admin/operators/" + operatorId).header("Authorization", token(adminId))
                        .contentType(APPLICATION_JSON).content(operatorUpdateBody("INACTIVE")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("OPERATOR_IN_USE"));
    }

    private Long id(String json) { return ((Number) JsonPath.read(json, "$.id")).longValue(); }
    private String token(Long id) { return "Bearer " + jwt.issue(id).value(); }

    private Long insertUser(String role, String prefix) {
        return jdbc.queryForObject("""
                INSERT INTO users(full_name,email,password,phone,role_id,status,email_verified)
                VALUES ('Test', ?, 'test-hash', '0901234567',
                        (SELECT id FROM roles WHERE name = ?), 'ACTIVE', true) RETURNING id
                """, Long.class, prefix + suffix + "@catalog-test.test", role);
    }

    private String locationBody(String name, String province, String address) {
        return "{\"name\":\"%s\",\"province\":\"%s\",\"address\":\"%s\"}"
                .formatted(name, province, address);
    }
    private String operatorCreateBody() {
        return "{\"name\":\"Nhà xe %s\",\"address\":\"Địa chỉ %s\",\"phone\":\"+84901234567\",\"email\":\"CONTACT.%s@EXAMPLE.COM\"}"
                .formatted(suffix, suffix, suffix);
    }
    private String operatorUpdateBody(String status) {
        return "{\"name\":\"Nhà xe %s\",\"address\":\"Địa chỉ %s\",\"phone\":\"0901234567\",\"email\":\"contact.%s@example.com\",\"status\":\"%s\"}"
                .formatted(suffix, suffix, suffix, status);
    }
    private String vehicleCreateBody() {
        return "{\"operatorId\":%d,\"licensePlate\":\"51a %s\",\"vehicleType\":\" Giường   nằm \",\"seatCapacity\":40}"
                .formatted(operatorId, suffix);
    }
    private String vehicleUpdateBody(String status) {
        return "{\"operatorId\":%d,\"licensePlate\":\"51A%s\",\"vehicleType\":\"Giường nằm\",\"seatCapacity\":40,\"status\":\"%s\"}"
                .formatted(operatorId, suffix.toUpperCase(), status);
    }
    private String routeCreateBody() {
        return "{\"originId\":%d,\"destinationId\":%d,\"estimatedDurationMinutes\":120}"
                .formatted(originId, destinationId);
    }
    private String routeUpdateBody(String status) {
        return "{\"originId\":%d,\"destinationId\":%d,\"estimatedDurationMinutes\":120,\"status\":\"%s\"}"
                .formatted(originId, destinationId, status);
    }
}
