package com.example.busbooking.security;

import com.example.busbooking.business.port.repository.UserRepository;
import com.example.busbooking.domain.enums.*;
import com.example.busbooking.domain.model.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentUserLoaderTest {
    private final UserRepository users = mock(UserRepository.class);
    private final CurrentUserLoader loader = new CurrentUserLoader(users);

    @Test
    void loadsOnlySafeIdentityAndReadsUpdatedRoleEachTime() {
        when(users.findById(42L)).thenReturn(Optional.of(user(UserStatus.ACTIVE, true, RoleName.USER)))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE, true, RoleName.ADMIN)));
        var first = loader.loadById(42L);
        assertThat(first.getName()).isEqualTo("42");
        assertThat(first.email()).isEqualTo("hoang@example.com");
        assertThat(first.role()).isEqualTo(RoleName.USER);
        assertThat(first.toString()).doesNotContain("hash-fixture");
        assertThat(loader.loadById(42L).role()).isEqualTo(RoleName.ADMIN);
        verify(users, times(2)).findById(42L);
    }

    @Test
    void deniesDeletedInactiveUnverifiedOrMissingRoleAccounts() {
        when(users.findById(42L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> loader.loadById(42L)).isInstanceOf(JwtAuthenticationException.class);
        for (User invalid : new User[] {user(UserStatus.INACTIVE, true, RoleName.USER),
                user(UserStatus.ACTIVE, false, RoleName.USER), user(UserStatus.ACTIVE, true, null)}) {
            when(users.findById(42L)).thenReturn(Optional.of(invalid));
            assertThatThrownBy(() -> loader.loadById(42L)).isInstanceOf(JwtAuthenticationException.class)
                    .hasMessage("Authentication is required");
        }
    }

    @Test
    void deniesInvalidIdsBeforeAccessingRepository() {
        for (Long id : new Long[] {null, 0L, -1L}) {
            assertThatThrownBy(() -> loader.loadById(id)).isInstanceOf(JwtAuthenticationException.class);
        }
        verifyNoInteractions(users);
    }

    @Test
    void databaseOutageIsNotDisguisedAsInvalidAuthentication() {
        when(users.findById(42L)).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> loader.loadById(42L)).isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }

    private User user(UserStatus status, boolean verified, RoleName role) {
        return new User(42L, "Hoang", "hoang@example.com", "hash-fixture", "0901234567",
                role == null ? null : new Role(1L, role), status, verified, null, null);
    }
}
