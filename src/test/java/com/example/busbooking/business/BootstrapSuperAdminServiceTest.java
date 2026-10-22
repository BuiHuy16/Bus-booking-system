package com.example.busbooking.business;

import com.example.busbooking.business.command.RegisterCommand;
import com.example.busbooking.business.port.repository.*;
import com.example.busbooking.business.port.security.PasswordHasher;
import com.example.busbooking.business.service.*;
import com.example.busbooking.domain.enums.*;
import com.example.busbooking.domain.exception.*;
import com.example.busbooking.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BootstrapSuperAdminServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final EmailVerificationService verification = mock(EmailVerificationService.class);
    private final BootstrapSuperAdminService bootstrap = new BootstrapSuperAdminService(users, roles, passwords, verification);
    private final RegisterCommand command = new RegisterCommand("Root", "root@example.com", "Password123", "0901234567");

    @BeforeEach
    void setup() {
        when(roles.findByName(RoleName.SUPER_ADMIN)).thenReturn(Optional.of(new Role(3L, RoleName.SUPER_ADMIN)));
        when(passwords.hash("Password123")).thenReturn("hash-fixture");
    }

    @Test
    void createsOnlyAnUnverifiedSuperAdminAndSendsOtp() {
        when(verification.register(any())).thenAnswer(invocation -> {
            User pending = invocation.getArgument(0);
            assertThat(pending.getRole().getName()).isEqualTo(RoleName.SUPER_ADMIN);
            assertThat(pending.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(pending.isEmailVerified()).isFalse();
            assertThat(pending.getPasswordHash()).isEqualTo("hash-fixture");
            return pending;
        });
        assertThat(bootstrap.bootstrap(command)).isEqualTo(BootstrapSuperAdminService.Result.CREATED_VERIFICATION_SENT);
    }

    @Test
    void existingSuperAdminIsNotResetReactivatedOrResentEvenIfUnverified() {
        when(users.findSuperAdmin()).thenReturn(Optional.of(root(UserStatus.INACTIVE, false)));
        assertThat(bootstrap.bootstrap(new RegisterCommand(null, " ROOT@EXAMPLE.COM ", null, null)))
                .isEqualTo(BootstrapSuperAdminService.Result.ALREADY_PRESENT);
        verifyNoInteractions(verification, passwords);
    }

    @Test
    void cannotReplaceTheSuperAdminWithAnotherEmail() {
        when(users.findSuperAdmin()).thenReturn(Optional.of(root(UserStatus.ACTIVE, true)));
        assertThatThrownBy(() -> bootstrap.bootstrap(new RegisterCommand("Root2", "other@example.com", "Password123", "0901234567")))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(verification);
    }

    @Test
    void existingOrdinaryAccountIsNeverPromotedOrReset() {
        when(users.findByEmail("root@example.com")).thenReturn(Optional.of(new User(2L, "User", "root@example.com", "old-hash", "0901234567",
                new Role(1L, RoleName.USER), UserStatus.ACTIVE, true, null, null)));
        assertThatThrownBy(() -> bootstrap.bootstrap(command)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(verification, passwords);
    }

    @Test
    void missingSeedOrInvalidInputFailsBeforeCreatingAnything() {
        when(roles.findByName(RoleName.SUPER_ADMIN)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> bootstrap.bootstrap(command)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> bootstrap.bootstrap(new RegisterCommand("Root", "bad", "short", "bad")))
                .isInstanceOf(InvalidAuthInputException.class);
        verifyNoInteractions(verification);
    }

    @Test
    void smtpFailureReturnsPendingStateForResendWithoutPretendingVerificationSucceeded() {
        when(verification.register(any())).thenThrow(new EmailDeliveryException());
        assertThat(bootstrap.bootstrap(command)).isEqualTo(BootstrapSuperAdminService.Result.CREATED_EMAIL_PENDING);
    }

    @Test
    void concurrentWinnerWithSameEmailIsIdempotent() {
        when(users.findSuperAdmin()).thenReturn(Optional.empty(), Optional.of(root(UserStatus.ACTIVE, false)));
        when(verification.register(any())).thenThrow(new SuperAdminAlreadyExistsException());
        assertThat(bootstrap.bootstrap(command)).isEqualTo(BootstrapSuperAdminService.Result.ALREADY_PRESENT);
    }

    private User root(UserStatus status, boolean verified) {
        return new User(1L, "Root", "root@example.com", "old-hash", "0901234567", new Role(3L, RoleName.SUPER_ADMIN), status, verified, null, null);
    }
}
