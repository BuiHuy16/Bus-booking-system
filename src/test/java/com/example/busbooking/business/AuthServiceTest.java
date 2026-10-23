package com.example.busbooking.business;

import com.example.busbooking.business.command.LoginCommand;
import com.example.busbooking.business.command.RegisterCommand;
import com.example.busbooking.business.port.repository.RoleRepository;
import com.example.busbooking.business.port.repository.UserRepository;
import com.example.busbooking.business.port.security.PasswordHasher;
import com.example.busbooking.business.port.security.TokenProvider;
import com.example.busbooking.business.result.IssuedToken;
import com.example.busbooking.business.service.AuthService;
import com.example.busbooking.business.service.EmailVerificationService;
import com.example.busbooking.domain.enums.RoleName;
import com.example.busbooking.domain.enums.UserStatus;
import com.example.busbooking.domain.exception.*;
import com.example.busbooking.domain.model.Role;
import com.example.busbooking.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final TokenProvider tokens = mock(TokenProvider.class);
    private final EmailVerificationService verification = mock(EmailVerificationService.class);
    private AuthService auth;
    private final Role role = new Role(1L, RoleName.USER);

    @BeforeEach
    void setUp() {
        when(passwords.hash(anyString())).thenReturn("hash-fixture");
        auth = new AuthService(users, roles, passwords, tokens, verification);
        when(roles.findByName(RoleName.USER)).thenReturn(Optional.of(role));
    }

    @Test
    void registersUserWithNormalizedContactAndUnverifiedEmail() {
        when(verification.register(any())).thenAnswer(invocation -> {
            User pending = invocation.getArgument(0);
            assertThat(pending.getEmail()).isEqualTo("hoang@example.com");
            assertThat(pending.getFullName()).isEqualTo("Nguyễn Văn Huy Hoàng");
            assertThat(pending.getPhone()).isEqualTo("0901234567");
            assertThat(pending.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(pending.isEmailVerified()).isFalse();
            assertThat(pending.getRole().getName()).isEqualTo(RoleName.USER);
            assertThat(pending.getPasswordHash()).isEqualTo("hash-fixture");
            return pending;
        });
        var result = auth.register(new RegisterCommand(" Nguyễn Văn Huy Hoàng ",
                " Hoang@Example.COM ", " Password!123 ", "+84901234567"));
        assertThat(result.emailVerified()).isFalse();
        assertThat(result.toString()).doesNotContain("hash-fixture", "Password!123");
        verify(passwords).hash(" Password!123 ");
        verifyNoInteractions(tokens);
    }

    @Test
    void rejectsDuplicateBeforeHashingOrSending() {
        when(users.existsByEmail("hoang@example.com")).thenReturn(true);
        assertThatThrownBy(() -> auth.register(validRegistration())).isInstanceOf(DuplicateEmailException.class);
        verifyNoInteractions(verification, tokens);
        verify(passwords, times(1)).hash(anyString()); // Only the constructor dummy hash.
    }

    @Test
    void missingSeedIsASystemError() {
        when(roles.findByName(RoleName.USER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> auth.register(validRegistration())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(verification, tokens);
    }

    @Test
    void rejectsInvalidRegistrationFieldsBeforePersistence() {
        for (RegisterCommand invalid : new RegisterCommand[] {
                new RegisterCommand(" ", "hoang@example.com", "Password123", "0901234567"),
                new RegisterCommand("Hoang", "bad-email", "Password123", "0901234567"),
                new RegisterCommand("Hoang", "hoang@example.com", "short", "0901234567"),
                new RegisterCommand("Hoang", "hoang@example.com", "a".repeat(73), "0901234567"),
                new RegisterCommand("Hoang", "hoang@example.com", "Password123", "+14155552671"),
                new RegisterCommand("Hoang", "hoang@example.com", "Password123", "0123456789"),
                new RegisterCommand("Hoang", "hoang@example.com", "Password123", null) }) {
            assertThatThrownBy(() -> auth.register(invalid)).isInstanceOf(InvalidAuthInputException.class);
        }
        assertThatThrownBy(() -> auth.register(null)).isInstanceOf(InvalidAuthInputException.class);
        verifyNoInteractions(users, verification, tokens);
    }

    @Test
    void acceptsVietnameseLandlineAndUnicodePassword() {
        when(verification.register(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(auth.register(new RegisterCommand("Hoang", "hoang@example.com",
                "ắ".repeat(24), "+842412345678")).phone()).isEqualTo("02412345678");
    }

    @Test
    void logsInOnlyVerifiedActiveUserAndUsesSeconds() {
        when(users.findByEmail("hoang@example.com")).thenReturn(Optional.of(user(UserStatus.ACTIVE, true)));
        when(passwords.matches("Password123", "hash-fixture")).thenReturn(true);
        when(tokens.issue(10L)).thenReturn(new IssuedToken("test-only-token", 3600));
        var result = auth.login(new LoginCommand(" HOANG@EXAMPLE.COM ", "Password123"));
        assertThat(result.token().expiresInSeconds()).isEqualTo(3600);
        assertThat(result.user().id()).isEqualTo(10L);
        assertThat(result.toString()).doesNotContain("test-only-token", "hash-fixture");
        verify(tokens).issue(10L);
    }

    @Test
    void deniesWrongPasswordAndDoesNotIssueToken() {
        when(users.findByEmail("hoang@example.com")).thenReturn(Optional.of(user(UserStatus.ACTIVE, true)));
        assertThatThrownBy(() -> auth.login(new LoginCommand("hoang@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Invalid email or password");
        verifyNoInteractions(tokens);
    }

    @Test
    void deniesInactiveOrUnverifiedWithTheSameMessage() {
        when(passwords.matches(anyString(), anyString())).thenReturn(true);
        for (User blocked : new User[] { user(UserStatus.INACTIVE, true), user(UserStatus.ACTIVE, false) }) {
            when(users.findByEmail("hoang@example.com")).thenReturn(Optional.of(blocked));
            assertThatThrownBy(() -> auth.login(new LoginCommand("hoang@example.com", "Password123")))
                    .isInstanceOf(InvalidCredentialsException.class).hasMessage("Invalid email or password");
        }
        verifyNoInteractions(tokens);
    }

    @Test
    void comparesAgainstCachedDummyHashForUnknownEmail() {
        when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> auth.login(new LoginCommand("missing@example.com", "Password123")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(passwords).matches("Password123", "hash-fixture");
        verify(passwords, times(1)).hash(anyString());
        verifyNoInteractions(tokens);
    }

    @Test
    void doesNotDisguiseDatabaseOrTokenProviderOutagesAsInvalidCredentials() {
        when(users.findByEmail("hoang@example.com")).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> auth.login(new LoginCommand("hoang@example.com", "Password123")))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(tokens);
    }

    @Test
    void tokenProviderFailureRemainsASystemError() {
        when(users.findByEmail("hoang@example.com")).thenReturn(Optional.of(user(UserStatus.ACTIVE, true)));
        when(passwords.matches("Password123", "hash-fixture")).thenReturn(true);
        when(tokens.issue(10L)).thenThrow(new IllegalStateException("token provider unavailable"));
        assertThatThrownBy(() -> auth.login(new LoginCommand("hoang@example.com", "Password123")))
                .isInstanceOf(IllegalStateException.class).hasMessage("token provider unavailable");
    }

    @Test
    void rejectsMissingOrMalformedLoginWithoutIssuingTokens() {
        assertThatThrownBy(() -> auth.login(null)).isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> auth.login(new LoginCommand("invalid", "Password123")))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(tokens);
    }

    @Test
    void sensitiveCommandsAndTokenResultsAreRedacted() {
        assertThat(validRegistration().toString()).doesNotContain("Password123");
        assertThat(new LoginCommand("hoang@example.com", "Password123").toString()).doesNotContain("Password123");
        assertThat(new com.example.busbooking.business.command.VerifyEmailCommand("hoang@example.com", "123456").toString())
                .doesNotContain("123456");
        assertThat(new IssuedToken("secret-token", 60).toString()).doesNotContain("secret-token");
    }

    private RegisterCommand validRegistration() {
        return new RegisterCommand("Hoang", "hoang@example.com", "Password123", "0901234567");
    }

    private User user(UserStatus status, boolean verified) {
        return new User(10L, "Hoang", "hoang@example.com", "hash-fixture", "0901234567",
                role, status, verified, null, null);
    }
}
