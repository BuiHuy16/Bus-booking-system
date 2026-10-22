package com.example.busbooking.api;

import com.example.busbooking.business.port.notification.EmailVerificationSender;
import com.example.busbooking.business.service.AuthService;
import com.example.busbooking.data.adapter.UserRepositoryAdapter;
import com.example.busbooking.domain.exception.EmailDeliveryException;
import com.example.busbooking.security.*;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.context.ApplicationContext;
import com.example.busbooking.security.ratelimit.AuthRateLimitFilter;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "auth.rate-limit.register-limit=500", "auth.rate-limit.login-limit=500",
        "auth.rate-limit.verify-limit=500", "auth.rate-limit.resend-limit=500",
        "auth.cors.allowed-origins=https://client.example"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(AuthHttpIntegrationTest.FixtureController.class)
class AuthHttpIntegrationTest {
    private static final String EMAIL = "http.phase3.hoang@example.com";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired JwtProperties jwtProperties;
    @Autowired FilterChainProxy securityChain;
    @Autowired ApplicationContext context;
    @MockitoBean EmailVerificationSender sender;
    @MockitoSpyBean UserRepositoryAdapter users;
    @MockitoSpyBean AuthService auth;
    private final AtomicReference<String> otp = new AtomicReference<>();

    @BeforeEach
    void setup() {
        cleanup();
        doAnswer(invocation -> { otp.set(invocation.getArgument(1)); return null; })
                .when(sender).send(anyString(), anyString(), any());
    }

    @AfterEach
    void cleanup() { jdbc.update("DELETE FROM users WHERE email LIKE 'http.phase3.%@example.com'"); }

    @Test
    void registersVerifiesLogsInAndReadsOwnProfileWithoutCreatingSession() throws Exception {
        var registered = register().andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.phone").value("0901234567"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(header().string("Cache-Control", containsString("no-store"))).andReturn();
        assertThat(registered.getRequest().getSession(false)).isNull();
        postJson("/api/auth/login", loginBody()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        postJson("/api/auth/verify-email", Map.of("email", EMAIL, "code", otp.get()))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        var login = postJson("/api/auth/login", loginBody()).andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(86400))
                .andExpect(jsonPath("$.user.emailVerified").value(true)).andReturn();
        String token = json.readTree(login.getResponse().getContentAsString()).get("token").asString();
        var me = mvc.perform(get("/api/auth/me").param("id", "999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn Huy Hoàng"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertThat(me.getRequest().getSession(false)).isNull();
        assertThat(jwt.verify(token).userId()).isPositive();
        postJson("/api/auth/verify-email", Map.of("email", EMAIL, "code", otp.get()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_VERIFICATION_CODE"));
    }

    @Test
    void rejectsInvalidFieldsUnicodeByteOverflowAndUnsupportedPrivilegeFields() throws Exception {
        for (String key : List.of("role", "status", "id", "emailVerified")) {
            var body = new HashMap<>(registrationBody());
            body.put(key, "ADMIN");
            postJson("/api/auth/register", body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
        }
        for (Map<String, Object> body : List.of(
                changed("phone", "+14155552671"), changed("password", "ắ".repeat(25)),
                changed("password", "short"), changed("email", "bad email"), changed("fullName", " "))) {
            postJson("/api/auth/register", body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Long.class, EMAIL)).isZero();
        verifyNoInteractions(sender);
    }

    @Test
    void acceptsTrimmedEmailAndBoundaryUnicodePasswordWithoutTrimmingThePassword() throws Exception {
        var body = changed("email", " HTTP.PHASE3.HOANG@EXAMPLE.COM ");
        body.put("password", "ắ".repeat(24));
        postJson("/api/auth/register", body).andExpect(status().isCreated());
        postJson("/api/auth/verify-email", Map.of("email", EMAIL, "code", otp.get())).andExpect(status().isNoContent());
        postJson("/api/auth/login", Map.of("email", EMAIL, "password", "ắ".repeat(24))).andExpect(status().isOk());
    }

    @Test
    void malformedOrMissingJsonIs400AndWrongContentTypeIs415() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_XML).content("<login/>"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
        postJson("/api/auth/verify-email", Map.of("email", EMAIL, "code", "12345"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.code").exists());
    }

    @Test
    void duplicateRegistrationReturns409() throws Exception {
        register().andExpect(status().isCreated());
        register().andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void concurrentHttpRegistrationCreatesOneUserAndOneEmail() throws Exception {
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> operation = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return register().andReturn().getResponse().getStatus();
            };
            var first = executor.submit(operation);
            var second = executor.submit(operation);
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        } finally { executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS); }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Long.class, EMAIL)).isEqualTo(1L);
        verify(sender, times(1)).send(eq(EMAIL), anyString(), any());
    }

    @Test
    void otpFailureCountCooldownAndResendAreExposedCorrectly() throws Exception {
        register().andExpect(status().isCreated());
        String correct = otp.get();
        String wrong = correct.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            postJson("/api/auth/verify-email", Map.of("email", EMAIL, "code", wrong))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_VERIFICATION_CODE"));
        }
        postJson("/api/auth/verify-email", Map.of("email", EMAIL, "code", correct)).andExpect(status().isBadRequest());
        postJson("/api/auth/resend-verification", Map.of("email", EMAIL)).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
        jdbc.update("UPDATE email_verifications SET sent_at = sent_at - INTERVAL '61 seconds' WHERE user_id = (SELECT id FROM users WHERE email = ?)", EMAIL);
        postJson("/api/auth/resend-verification", Map.of("email", EMAIL)).andExpect(status().isAccepted());
        postJson("/api/auth/verify-email", Map.of("email", EMAIL, "code", otp.get())).andExpect(status().isNoContent());
        postJson("/api/auth/resend-verification", Map.of("email", "http.phase3.missing@example.com"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.message").exists());
    }

    @Test
    void expiredOtpIsRejectedAndSmtpFailureLeavesARecoverableAccount() throws Exception {
        doThrow(new EmailDeliveryException()).when(sender).send(anyString(), anyString(), any());
        register().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("EMAIL_DELIVERY_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT email_verified FROM users WHERE email = ?", Boolean.class, EMAIL)).isFalse();
        postJson("/api/auth/login", loginBody()).andExpect(status().isUnauthorized());
        doAnswer(invocation -> { otp.set(invocation.getArgument(1)); return null; })
                .when(sender).send(anyString(), anyString(), any());
        jdbc.update("UPDATE email_verifications SET sent_at = sent_at - INTERVAL '11 minutes', expires_at = expires_at - INTERVAL '11 minutes' WHERE user_id = (SELECT id FROM users WHERE email = ?)", EMAIL);
        postJson("/api/auth/resend-verification", Map.of("email", EMAIL)).andExpect(status().isAccepted());
        jdbc.update("UPDATE email_verifications SET sent_at = sent_at - INTERVAL '11 minutes', expires_at = expires_at - INTERVAL '11 minutes' WHERE user_id = (SELECT id FROM users WHERE email = ?)", EMAIL);
        postJson("/api/auth/verify-email", Map.of("email", EMAIL, "code", otp.get()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void jwtErrorsAndMissingTokensUseJson401EvenOnPublicEndpoints() throws Exception {
        mvc.perform(get("/api/auth/me?token=do-not-echo"))
                .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"))
                .andExpect(content().string(not(containsString("do-not-echo"))));
        postJsonWithToken("/api/auth/login", loginBody(), "invalid-token")
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer a", "Bearer b"))
                .andExpect(status().isUnauthorized());
        String expired = new JwtService(jwtProperties, Clock.fixed(Instant.now().minusSeconds(86401), ZoneOffset.UTC))
                .issue(42L).value();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
    }

    @Test
    void actualChainEnforcesRolesAndPublicRulesByMethod() throws Exception {
        String token = verifiedToken();
        mvc.perform(get("/api/admin/phase3-check").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mvc.perform(get("/api/trips")).andExpect(status().isOk());
        mvc.perform(post("/api/trips").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/not-configured").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        jdbc.update("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'ADMIN') WHERE email = ?", EMAIL);
        mvc.perform(get("/api/admin/phase3-check").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/post-only").header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
        mvc.perform(get("/api/admin/unknown").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void tokenCannotBeUsedAfterAccountIsDisabledDeletedOrUnverified() throws Exception {
        String token = verifiedToken();
        jdbc.update("UPDATE users SET status = 'INACTIVE' WHERE email = ?", EMAIL);
        me(token).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE users SET status = 'ACTIVE', email_verified = FALSE WHERE email = ?", EMAIL);
        me(token).andExpect(status().isUnauthorized());
        jdbc.update("DELETE FROM users WHERE email = ?", EMAIL);
        me(token).andExpect(status().isUnauthorized());
    }

    @Test
    void infrastructureFailuresInFilterAndMvcAre500WithoutLeakingDetails() throws Exception {
        String token = verifiedToken();
        long id = jwt.verify(token).userId();
        doThrow(new IllegalStateException("secret SQL password hash token")).when(users).findById(id);
        me(token).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("secret SQL"))));
        doThrow(new IllegalStateException("sensitive provider failure")).when(auth).login(any());
        postJson("/api/auth/login", loginBody()).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"));
    }

    @Test
    void accountRaceBetweenFilterAndMeQueryIs401RatherThanNullPointer() throws Exception {
        String token = verifiedToken();
        doThrow(new com.example.busbooking.domain.exception.InvalidCredentialsException()).when(auth).currentUser(anyLong());
        me(token).andExpect(status().isUnauthorized());
    }

    @Test
    void corsPreflightIsAllowlistedAndDeniedOriginsGetJson403() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", "https://client.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://client.example"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(options("/api/auth/login").header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void openApiDeclaresBearerOnlyOnProtectedAuthAndSwaggerIsAvailableInTest() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/auth/me'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/users/me'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/users/me'].put.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/admin/users/{userId}/role'].put.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/admin/users/{userId}/role'].put.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/verify-email'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/resend-verification'].post.responses['202']").exists());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void errorDispatchKeepsItsOriginalStatusWhileDirectErrorRouteIsNotPublic() throws Exception {
        mvc.perform(get("/error").with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; })
                        .requestAttr("jakarta.servlet.error.status_code", 500)
                        .requestAttr("jakarta.servlet.error.request_uri", "/api/auth/me"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"));
        mvc.perform(get("/error")).andExpect(status().isUnauthorized());
    }

    @Test
    void filtersAreRegisteredExactlyOnceAndAuthenticationDoesNotLeakToNextRequest() throws Exception {
        var filters = securityChain.getFilterChains().getFirst().getFilters();
        assertThat(filters.stream().filter(JwtAuthenticationFilter.class::isInstance).count()).isEqualTo(1);
        assertThat(filters.stream().filter(AuthRateLimitFilter.class::isInstance).count()).isEqualTo(1);
        assertThat(context.getBeansOfType(JwtAuthenticationFilter.class)).isEmpty();
        assertThat(context.getBeansOfType(AuthRateLimitFilter.class)).isEmpty();
        String token = verifiedToken();
        me(token).andExpect(status().isOk());
        verify(users, times(2)).findById(anyLong()); // JWT loader and current-user query, once each.
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    private ResultActions register() throws Exception { return postJson("/api/auth/register", registrationBody()); }
    private ResultActions me(String token) throws Exception { return mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)); }
    private ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)));
    }
    private ResultActions postJsonWithToken(String path, Object body, String token) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token).content(json.writeValueAsBytes(body)));
    }
    private String verifiedToken() throws Exception {
        register().andExpect(status().isCreated());
        postJson("/api/auth/verify-email", Map.of("email", EMAIL, "code", otp.get())).andExpect(status().isNoContent());
        var result = postJson("/api/auth/login", loginBody()).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("token").asString();
    }
    private Map<String, Object> registrationBody() {
        return Map.of("fullName", "Nguyễn Văn Huy Hoàng", "email", EMAIL, "password", "Password123", "phone", "+84901234567");
    }
    private Map<String, Object> changed(String key, Object value) {
        var body = new HashMap<>(registrationBody()); body.put(key, value); return body;
    }
    private Map<String, Object> loginBody() { return Map.of("email", EMAIL, "password", "Password123"); }

    @RestController
    static class FixtureController {
        @GetMapping("/api/admin/phase3-check") Map<String, String> admin() { return Map.of("result", "ok"); }
        @PostMapping("/api/admin/post-only") Map<String, String> postOnly() { return Map.of("result", "ok"); }
        @GetMapping("/api/trips") List<String> trips() { return List.of(); }
    }
}
