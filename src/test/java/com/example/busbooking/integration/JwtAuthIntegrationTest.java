package com.example.busbooking.integration;

import com.example.busbooking.business.command.*;
import com.example.busbooking.business.port.notification.EmailVerificationSender;
import com.example.busbooking.business.port.repository.UserRepository;
import com.example.busbooking.business.port.security.TokenProvider;
import com.example.busbooking.business.service.AuthService;
import com.example.busbooking.business.service.EmailVerificationService;
import com.example.busbooking.domain.enums.RoleName;
import com.example.busbooking.domain.exception.InvalidCredentialsException;
import com.example.busbooking.security.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class JwtAuthIntegrationTest {
    private static final String EMAIL = "jwt.phase2.hoang@example.com";
    @Autowired private AuthService auth;
    @Autowired private EmailVerificationService verification;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    @Autowired private TokenProvider tokenProvider;
    @Autowired private CurrentUserLoader loader;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationContext context;
    @MockitoBean private EmailVerificationSender sender;
    private final AtomicReference<String> otp = new AtomicReference<>();

    @BeforeEach
    void setup() {
        cleanup();
        doAnswer(invocation -> {
            otp.set(invocation.getArgument(1));
            return null;
        }).when(sender).send(eq(EMAIL), anyString(), any());
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM users WHERE email LIKE 'jwt.phase2.%@example.com'");
        SecurityContextHolder.clearContext();
    }

    @Test
    void productionWiringIssuesRealJwtOnlyAfterEmailVerification() {
        var registered = auth.register(registration());
        assertThat(registered.emailVerified()).isFalse();
        assertThatThrownBy(() -> auth.login(login())).isInstanceOf(InvalidCredentialsException.class);
        verification.verify(new VerifyEmailCommand(EMAIL, otp.get()));
        var result = auth.login(login());
        assertThat(tokenProvider).isSameAs(jwt);
        assertThat(jwt.verify(result.token().value()).userId()).isEqualTo(registered.id());
        assertThat(result.token().expiresInSeconds()).isEqualTo(86400);
        assertThat(users.findById(registered.id()).orElseThrow().getPasswordHash()).startsWith("$2a$10$");
    }

    @Test
    void filterUsesDatabaseIdentityAndAppliesRoleChangesToTheSameToken() throws Exception {
        String token = verifiedToken();
        assertThat(authenticate(token).role()).isEqualTo(RoleName.USER);
        jdbc.update("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'ADMIN') WHERE email = ?", EMAIL);
        var principal = authenticate(token);
        assertThat(principal.role()).isEqualTo(RoleName.ADMIN);
        assertThat(principal.email()).isEqualTo(EMAIL);
    }

    @Test
    void validTokenCannotAuthenticateAfterAccountIsDisabled() throws Exception {
        String token = verifiedToken();
        jdbc.update("UPDATE users SET status = 'INACTIVE' WHERE email = ?", EMAIL);
        assertDenied(token);
        assertThatThrownBy(() -> auth.login(login())).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void validTokenCannotAuthenticateAfterAccountDeletion() throws Exception {
        String token = verifiedToken();
        jdbc.update("DELETE FROM users WHERE email = ?", EMAIL);
        assertDenied(token);
        assertThat(jwt.verify(token).userId()).isPositive(); // Cryptographic validity is not account eligibility.
    }

    @Test
    void validTokenCannotAuthenticateAfterEmailVerificationIsRevoked() throws Exception {
        String token = verifiedToken();
        jdbc.update("UPDATE users SET email_verified = FALSE WHERE email = ?", EMAIL);
        assertDenied(token);
    }

    @Test
    void changingTheStoredEmailIsReflectedWithoutTrustingTokenClaims() throws Exception {
        String token = verifiedToken();
        jdbc.update("UPDATE users SET email = 'jwt.phase2.changed@example.com' WHERE email = ?", EMAIL);
        assertThat(authenticate(token).email()).isEqualTo("jwt.phase2.changed@example.com");
    }

    @Test
    void filterIsNotAutoRegisteredAsAServletOrSecurityBean() {
        assertThat(context.getBeansOfType(JwtAuthenticationFilter.class)).isEmpty();
        assertThat(context.getBeansOfType(AuthService.class)).hasSize(1);
        assertThat(context.getBeansOfType(TokenProvider.class)).hasSize(1);
    }

    private String verifiedToken() {
        auth.register(registration());
        verification.verify(new VerifyEmailCommand(EMAIL, otp.get()));
        return auth.login(login()).token().value();
    }

    private AuthenticatedPrincipal authenticate(String token) throws Exception {
        var captured = new AtomicReference<AuthenticatedPrincipal>();
        var response = new MockHttpServletResponse();
        filter().doFilter(request(token), response, (request, res) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            var principal = (AuthenticatedPrincipal) authentication.getPrincipal();
            assertThat(authentication.getAuthorities()).extracting("authority")
                    .containsExactly("ROLE_" + principal.role().name());
            assertThat(authentication.getCredentials()).isNull();
            captured.set(principal);
        });
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(captured.get()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        return captured.get();
    }

    private void assertDenied(String token) throws Exception {
        var response = new MockHttpServletResponse();
        filter().doFilter(request(token), response, (request, res) -> { throw new AssertionError("Controller reached"); });
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private JwtAuthenticationFilter filter() {
        return new JwtAuthenticationFilter(jwt, loader, (request, response, failure) -> response.setStatus(401));
    }

    private MockHttpServletRequest request(String token) {
        var request = new MockHttpServletRequest("GET", "/fixture/protected");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private RegisterCommand registration() {
        return new RegisterCommand("Nguyễn Văn Huy Hoàng", EMAIL, "Password123", "0901234567");
    }

    private LoginCommand login() { return new LoginCommand(EMAIL, "Password123"); }
}
