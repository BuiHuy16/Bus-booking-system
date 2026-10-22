package com.example.busbooking.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

// Opt-in: requires PostgreSQL test database and local Mailpit SMTP:1025 / API:8025.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.address=127.0.0.1")
@ActiveProfiles("test")
class AuthLiveHttpSmokeIT {
    @Value("${local.server.port}") int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void registersThroughRealHttpRetrievesOtpFromMailpitAndUsesJwt() throws Exception {
        String email = "live.phase3." + UUID.randomUUID() + "@example.com";
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var register = post(client, "/api/auth/register", Map.of("fullName", "Hoang HTTP Smoke",
                    "email", email, "password", "SmokePassword123", "phone", "0901234567"));
            assertThat(register.statusCode()).isEqualTo(201);
            assertThat(json.readTree(register.body()).get("emailVerified").asBoolean()).isFalse();
            var denied = post(client, "/api/auth/login", Map.of("email", email, "password", "SmokePassword123"));
            assertThat(denied.statusCode()).isEqualTo(401);

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
            assertThat(code).as("OTP delivered to the local Mailpit inbox").isNotNull();
            assertThat(post(client, "/api/auth/verify-email", Map.of("email", email, "code", code)).statusCode()).isEqualTo(204);
            var login = post(client, "/api/auth/login", Map.of("email", email, "password", "SmokePassword123"));
            assertThat(login.statusCode()).isEqualTo(200);
            String token = json.readTree(login.body()).get("token").asString();
            var me = client.send(HttpRequest.newBuilder(url("/api/auth/me"))
                    .header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(me.statusCode()).isEqualTo(200);
            assertThat(json.readTree(me.body()).get("email").asString()).isEqualTo(email);
            assertThat(json.readTree(me.body()).get("emailVerified").asBoolean()).isTrue();
            assertThat(me.headers().firstValue("Set-Cookie")).isEmpty();
        } finally {
            jdbc.update("DELETE FROM users WHERE email = ?", email);
        }
    }

    private URI url(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    private HttpResponse<String> post(HttpClient client, String path, Object body) throws Exception {
        return client.send(HttpRequest.newBuilder(url(path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
}
