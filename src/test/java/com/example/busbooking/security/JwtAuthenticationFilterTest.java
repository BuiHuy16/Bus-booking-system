package com.example.busbooking.security;

import com.example.busbooking.business.port.repository.UserRepository;
import com.example.busbooking.domain.enums.*;
import com.example.busbooking.domain.model.*;
import jakarta.servlet.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.busbooking.security.JwtTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final CurrentUserLoader loader = new CurrentUserLoader(repository);
    private final JwtService jwt = new JwtService(properties(), CLOCK);
    private final AtomicInteger denied = new AtomicInteger();
    private final AuthenticationEntryPoint entryPoint = (request, response, failure) -> {
        denied.incrementAndGet();
        response.setStatus(401);
    };
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, loader, entryPoint);

    @BeforeEach
    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @BeforeEach
    void setupUser() {
        when(repository.findById(42L)).thenReturn(Optional.of(new User(42L, "Hoang", "hoang@example.com",
                "hash-fixture", "0901234567", new Role(1L, RoleName.USER), UserStatus.ACTIVE, true, null, null)));
    }

    @Test
    void validMixedCaseBearerSetsIdentityWithoutCredentialsAndClearsItAfterwards() throws Exception {
        var request = request("bEaReR " + jwt.issue(42L).value());
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication.isAuthenticated()).isTrue();
            assertThat(authentication.getPrincipal()).isEqualTo(new AuthenticatedPrincipal(42L, "hoang@example.com", RoleName.USER));
            assertThat(authentication.getCredentials()).isNull();
            assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        });
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(denied).hasValue(0);
    }

    @Test
    void noHeaderOrNonBearerSchemePassesThroughWithoutAuthentication() throws Exception {
        for (String header : new String[] {null, "Basic abc", "Digest abc"}) {
            var called = new AtomicInteger();
            filter.doFilter(request(header), new MockHttpServletResponse(), (req, res) -> {
                called.incrementAndGet();
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            });
            assertThat(called).hasValue(1);
        }
        verifyNoInteractions(repository);
    }

    @Test
    void malformedBearerStopsBeforeControllerAndClearsContext() throws Exception {
        for (String header : List.of("Bearer", "Bearer ", "Bearer a b", "Bearer\tabc", " Bearer abc", "Bearer bad-token")) {
            var response = new MockHttpServletResponse();
            filter.doFilter(request(header), response, (req, res) -> { throw new AssertionError("Controller was called"); });
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
        verifyNoInteractions(repository);
    }

    @Test
    void duplicateAuthorizationHeadersAreRejected() throws Exception {
        var request = request("Bearer " + jwt.issue(42L).value());
        request.addHeader("Authorization", "Bearer " + jwt.issue(42L).value());
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { throw new AssertionError("Controller was called"); });
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(repository);
    }

    @Test
    void expiredTokenIsRejectedBeforeDatabaseAccess() throws Exception {
        var expiredFilter = new JwtAuthenticationFilter(new JwtService(properties(), Clock.fixed(NOW.plusSeconds(3600), ZoneOffset.UTC)), loader, entryPoint);
        var response = new MockHttpServletResponse();
        expiredFilter.doFilter(request("Bearer " + jwt.issue(42L).value()), response,
                (req, res) -> { throw new AssertionError("Controller was called"); });
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(repository);
    }

    @Test
    void missingUserBecomes401WithoutReachingController() throws Exception {
        when(repository.findById(42L)).thenReturn(Optional.empty());
        var response = new MockHttpServletResponse();
        filter.doFilter(request("Bearer " + jwt.issue(42L).value()), response,
                (req, res) -> { throw new AssertionError("Controller was called"); });
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void keepsExistingAuthenticationButStillRejectsAnInvalidSuppliedBearer() throws Exception {
        var existing = UsernamePasswordAuthenticationToken.authenticated("existing", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        filter.doFilter(request("Bearer " + jwt.issue(42L).value()), new MockHttpServletResponse(),
                (req, res) -> assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
        var response = new MockHttpServletResponse();
        filter.doFilter(request("Bearer invalid"), response, (req, res) -> { throw new AssertionError("Controller was called"); });
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void databaseErrorsArePropagatedWithoutCallingEntryPoint() {
        when(repository.findById(42L)).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> filter.doFilter(request("Bearer " + jwt.issue(42L).value()), new MockHttpServletResponse(),
                (req, res) -> { throw new AssertionError("Controller was called"); }))
                .isInstanceOf(IllegalStateException.class).hasMessage("database unavailable");
        assertThat(denied).hasValue(0);
    }

    @Test
    void downstreamExceptionsArePropagatedAndOwnedContextIsCleared() {
        for (Exception failure : List.of(new ServletException("controller failed"),
                new IOException("connection failed"), new IllegalStateException("downstream failed"))) {
            assertThatThrownBy(() -> filter.doFilter(request("Bearer " + jwt.issue(42L).value()), new MockHttpServletResponse(),
                    (req, res) -> {
                        if (failure instanceof ServletException servlet) { throw servlet; }
                        if (failure instanceof IOException io) { throw io; }
                        throw (RuntimeException) failure;
                    })).isSameAs(failure);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
        assertThat(denied).hasValue(0);
    }

    @Test
    void nestedInvocationOnTheSameRequestDoesNotAuthenticateTwice() throws Exception {
        var request = request("Bearer " + jwt.issue(42L).value());
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> filter.doFilter(req, res, (nestedReq, nestedRes) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        }));
        verify(repository, times(1)).findById(42L);
    }

    private MockHttpServletRequest request(String header) {
        var request = new MockHttpServletRequest("GET", "/fixture/protected");
        if (header != null) { request.addHeader("Authorization", header); }
        return request;
    }
}
