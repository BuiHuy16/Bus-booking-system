package com.example.busbooking.notification;

import com.example.busbooking.business.port.notification.EmailVerificationSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// Explicit opt-in: bash mvnw -Dtest=SmtpMailpitSmokeIT test (Mailpit must be running).
@SpringBootTest(properties = {"spring.mail.host=127.0.0.1", "spring.mail.port=1025"})
@ActiveProfiles("test")
class SmtpMailpitSmokeIT {
    @Autowired private EmailVerificationSender sender;
    @Autowired private ObjectMapper json;

    @Test
    void sendsOnlyToTheLocalMailpitInbox() throws Exception {
        String recipient = "smtp.smoke." + UUID.randomUUID() + "@example.com";
        String code = "012345";
        sender.send(recipient, code, Instant.now().plusSeconds(600));
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8025/api/v1/messages"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                int matches = 0;
                for (var message : json.readTree(response.body()).get("messages")) {
                    boolean addressedToThisRun = false;
                    for (var address : message.get("To")) {
                        if (recipient.equals(address.get("Address").asString())) { addressedToThisRun = true; }
                    }
                    if (addressedToThisRun) {
                        assertThat(message.get("Snippet").asString()).contains(code);
                        matches++;
                    }
                }
                assertThat(matches).as("message addressed to this smoke run").isEqualTo(1);
            });
        }
    }
}
