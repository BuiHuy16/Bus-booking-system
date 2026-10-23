package com.example.busbooking.api;

import com.example.busbooking.business.service.AuthService;
import com.example.busbooking.domain.exception.InvalidCredentialsException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"auth.rate-limit.login-limit=2", "springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthRateLimitHttpTest {
    private static final AtomicInteger IPS = new AtomicInteger();
    @Autowired MockMvc mvc;
    @MockitoBean AuthService auth;
    private String ip;
    private static final String BODY = "{\"email\":\"hoang@example.com\",\"password\":\"Password123\"}";

    @BeforeEach
    void setup() {
        ip = "192.0.2." + IPS.incrementAndGet();
        when(auth.login(any())).thenThrow(new InvalidCredentialsException());
    }

    @Test
    void countsFailuresIgnoresForgedForwardedHeadersAndProvidesRetryAfter() throws Exception {
        attempt(BODY, "1.1.1.1").andExpect(status().isUnauthorized());
        attempt(BODY, "2.2.2.2").andExpect(status().isUnauthorized());
        var blocked = attempt(BODY, "3.3.3.3").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After")).andReturn();
        assertThat(Integer.parseInt(blocked.getResponse().getHeader("Retry-After"))).isBetween(1, 60);
        verify(auth, times(2)).login(any());
    }

    @Test
    void malformedBodyCountsAndTheFilterDoesNotConsumeTheBody() throws Exception {
        attempt("{broken", "1.1.1.1").andExpect(status().isBadRequest());
        attempt(BODY, "1.1.1.1").andExpect(status().isUnauthorized());
        attempt(BODY, "1.1.1.1").andExpect(status().isTooManyRequests());
        verify(auth, times(1)).login(any());
    }

    @Test
    void encodedEndpointCannotBypassProtection() throws Exception {
        attempt(BODY, "1.1.1.1").andExpect(status().isUnauthorized());
        attempt(BODY, "1.1.1.1").andExpect(status().isUnauthorized());
        var response = mvc.perform(post(URI.create("/api/auth/%6cogin")).with(request -> { request.setRemoteAddr(ip); return request; })
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andReturn().getResponse();
        assertThat(response.getStatus()).isIn(400, 429);
        verify(auth, times(2)).login(any());
    }

    @Test
    void docsAreNotPublicWhenDisabled() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    }

    private ResultActions attempt(String body, String forwarded) throws Exception {
        return mvc.perform(post("/api/auth/login").with(request -> { request.setRemoteAddr(ip); return request; })
                .header("X-Forwarded-For", forwarded).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
