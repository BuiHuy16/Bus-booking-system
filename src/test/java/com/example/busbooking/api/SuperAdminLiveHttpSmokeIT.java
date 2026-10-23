package com.example.busbooking.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

// Opt-in. PostgreSQL test + Mailpit are required; credentials below are disposable test fixtures.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "spring.mail.host=127.0.0.1", "auth.bootstrap-super-admin.enabled=true",
        "auth.bootstrap-super-admin.password=SmokeRootPassword123",
        "auth.bootstrap-super-admin.full-name=Root Smoke", "auth.bootstrap-super-admin.phone=0901234567"})
@ActiveProfiles("test")
class SuperAdminLiveHttpSmokeIT {
    private static final String ROOT = "live.phase4.root." + UUID.randomUUID() + "@example.com";
    private final String employee = "live.phase4.employee." + UUID.randomUUID() + "@example.com";
    @Value("${local.server.port}") int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void rootEmail(DynamicPropertyRegistry registry) { registry.add("auth.bootstrap-super-admin.email", () -> ROOT); }

    @Test
    void bootstrapsOnlyRootVerifiesBothEmailsUpdatesProfileAndManagesRolesThroughHttp() throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            assertThat(post(client, "/api/auth/login", Map.of("email", ROOT, "password", "SmokeRootPassword123")).statusCode()).isEqualTo(401);
            verify(client, ROOT);
            String rootToken = login(client, ROOT, "SmokeRootPassword123");
            var registered = post(client, "/api/auth/register", Map.of("fullName", "Employee Smoke", "email", employee,
                    "password", "SmokeEmployee123", "phone", "0901234567"));
            assertThat(registered.statusCode()).isEqualTo(201);
            long employeeId = json.readTree(registered.body()).get("id").asLong();
            assertThat(json.readTree(registered.body()).get("role").asString()).isEqualTo("USER");
            verify(client, employee);
            String employeeToken = login(client, employee, "SmokeEmployee123");
            String rolePath = "/api/admin/users/" + employeeId + "/role";
            assertThat(put(client, rolePath, Map.of("role", "ADMIN"), employeeToken).statusCode()).isEqualTo(403);
            var profile = put(client, "/api/users/me", Map.of("fullName", "Employee Updated", "phone", "+84912345678"), employeeToken);
            assertThat(profile.statusCode()).isEqualTo(200);
            assertThat(json.readTree(profile.body()).get("phone").asString()).isEqualTo("0912345678");
            assertThat(put(client, rolePath, Map.of("role", "ADMIN"), rootToken).statusCode()).isEqualTo(200);
            assertThat(json.readTree(get(client, "/api/users/me", employeeToken).body()).get("role").asString()).isEqualTo("ADMIN");
            assertThat(put(client, rolePath, Map.of("role", "USER"), employeeToken).statusCode()).isEqualTo(403);
            assertThat(put(client, rolePath, Map.of("role", "USER"), rootToken).statusCode()).isEqualTo(200);
            assertThat(json.readTree(get(client, "/api/users/me", employeeToken).body()).get("role").asString()).isEqualTo("USER");
            assertThat(json.readTree(get(client, "/api/users/me", rootToken).body()).get("role").asString()).isEqualTo("SUPER_ADMIN");
        } finally {
            jdbc.update("DELETE FROM users WHERE email IN (?, ?)", ROOT, employee);
        }
    }

    private void verify(HttpClient client, String email) throws Exception {
        var inbox = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:8025/api/v1/messages"))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(inbox.statusCode()).isEqualTo(200);
        String code = null;
        for (var message : json.readTree(inbox.body()).get("messages")) {
            for (var recipient : message.get("To")) {
                if (email.equals(recipient.get("Address").asString())) {
                    var matcher = Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])").matcher(message.get("Snippet").asString());
                    if (matcher.find()) { code = matcher.group(); }
                }
            }
        }
        assertThat(code).as("OTP delivered to Mailpit").isNotNull();
        assertThat(post(client, "/api/auth/verify-email", Map.of("email", email, "code", code)).statusCode()).isEqualTo(204);
    }

    private String login(HttpClient client, String email, String password) throws Exception {
        var result = post(client, "/api/auth/login", Map.of("email", email, "password", password));
        assertThat(result.statusCode()).isEqualTo(200);
        return json.readTree(result.body()).get("token").asString();
    }
    private URI url(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    private HttpResponse<String> post(HttpClient client, String path, Object body) throws Exception { return send(client, "POST", path, body, null); }
    private HttpResponse<String> put(HttpClient client, String path, Object body, String token) throws Exception { return send(client, "PUT", path, body, token); }
    private HttpResponse<String> get(HttpClient client, String path, String token) throws Exception { return send(client, "GET", path, null, token); }
    private HttpResponse<String> send(HttpClient client, String method, String path, Object body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(url(path)).timeout(Duration.ofSeconds(10));
        if (token != null) { builder.header("Authorization", "Bearer " + token); }
        if (body != null) { builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))); }
        else { builder.GET(); }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
