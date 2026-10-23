package com.example.busbooking.business;

import com.example.busbooking.business.command.UpdateProfileCommand;
import com.example.busbooking.business.port.repository.UserRepository;
import com.example.busbooking.business.service.UserService;
import com.example.busbooking.domain.enums.*;
import com.example.busbooking.domain.exception.*;
import com.example.busbooking.domain.model.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserService service = new UserService(users);

    @Test
    void updatesOnlyNameAndPhoneThroughTheDedicatedPort() {
        User stored = user(UserStatus.ACTIVE, true);
        when(users.findById(1L)).thenReturn(Optional.of(stored));
        when(users.updateProfile(1L, "Hoang Updated", "0901234567")).thenReturn(Optional.of(stored));
        var result = service.updateProfile(1L, new UpdateProfileCommand(" Hoang Updated ", "+84901234567"));
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.toString()).doesNotContain("hash-fixture");
        verify(users).updateProfile(1L, "Hoang Updated", "0901234567");
        verify(users, never()).save(any());
    }

    @Test
    void rejectsInvalidDataAndMissingFields() {
        for (UpdateProfileCommand invalid : new UpdateProfileCommand[] {null,
                new UpdateProfileCommand(null, "0901234567"), new UpdateProfileCommand("Hoang", null),
                new UpdateProfileCommand(" ", "0901234567"), new UpdateProfileCommand("Hoang", "+14155552671")}) {
            assertThatThrownBy(() -> service.updateProfile(1L, invalid)).isInstanceOf(InvalidAuthInputException.class);
        }
        verifyNoInteractions(users);
    }

    @Test
    void deniesMissingInactiveOrUnverifiedAccounts() {
        when(users.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getProfile(1L)).isInstanceOf(InvalidCredentialsException.class);
        for (User invalid : new User[] {user(UserStatus.INACTIVE, true), user(UserStatus.ACTIVE, false)}) {
            when(users.findById(1L)).thenReturn(Optional.of(invalid));
            assertThatThrownBy(() -> service.getProfile(1L)).isInstanceOf(InvalidCredentialsException.class);
        }
        assertThatThrownBy(() -> service.getProfile(null)).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void revokedAccountBetweenReadAndUpdateIsNotReactivated() {
        when(users.findById(1L)).thenReturn(Optional.of(user(UserStatus.ACTIVE, true)));
        when(users.updateProfile(1L, "Hoang", "0901234567")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateProfile(1L, new UpdateProfileCommand("Hoang", "0901234567")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(users, never()).save(any());
    }

    @Test
    void databaseFailureRemainsAnInfrastructureError() {
        when(users.findById(1L)).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> service.getProfile(1L)).isInstanceOf(IllegalStateException.class);
    }

    private User user(UserStatus status, boolean verified) {
        return new User(1L, "Hoang", "hoang@example.com", "hash-fixture", "0901234567",
                new Role(1L, RoleName.USER), status, verified, null, null);
    }
}
