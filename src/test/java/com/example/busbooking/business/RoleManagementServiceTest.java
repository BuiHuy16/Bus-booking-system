package com.example.busbooking.business;

import com.example.busbooking.business.port.repository.*;
import com.example.busbooking.business.service.RoleManagementService;
import com.example.busbooking.domain.enums.*;
import com.example.busbooking.domain.exception.*;
import com.example.busbooking.domain.model.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoleManagementServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RoleManagementRepository roles = mock(RoleManagementRepository.class);
    private final RoleManagementService service = new RoleManagementService(users, roles);

    @Test
    void superAdminPromotesVerifiedActiveUserAndCanDemoteAdmin() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L, RoleName.SUPER_ADMIN, UserStatus.ACTIVE, true)));
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, RoleName.USER, UserStatus.ACTIVE, true)));
        when(roles.changeRole(1L, 2L, RoleName.ADMIN)).thenReturn(user(2L, RoleName.ADMIN, UserStatus.ACTIVE, true));
        assertThat(service.changeRole(1L, 2L, RoleName.ADMIN).role()).isEqualTo(RoleName.ADMIN);
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, RoleName.ADMIN, UserStatus.INACTIVE, false)));
        when(roles.changeRole(1L, 2L, RoleName.USER)).thenReturn(user(2L, RoleName.USER, UserStatus.INACTIVE, false));
        assertThat(service.changeRole(1L, 2L, RoleName.USER).role()).isEqualTo(RoleName.USER);
    }

    @Test
    void ordinaryAdminOrUserCannotManageRoles() {
        for (RoleName role : new RoleName[] {RoleName.USER, RoleName.ADMIN}) {
            when(users.findById(1L)).thenReturn(Optional.of(user(1L, role, UserStatus.ACTIVE, true)));
            assertThatThrownBy(() -> service.changeRole(1L, 2L, RoleName.ADMIN)).isInstanceOf(ForbiddenOperationException.class);
        }
        verifyNoInteractions(roles);
    }

    @Test
    void cannotAssignSuperAdminOrChangeTheSuperAdminsOwnRole() {
        var root = user(1L, RoleName.SUPER_ADMIN, UserStatus.ACTIVE, true);
        when(users.findById(1L)).thenReturn(Optional.of(root));
        assertThatThrownBy(() -> service.changeRole(1L, 2L, RoleName.SUPER_ADMIN)).isInstanceOf(InvalidAuthInputException.class);
        assertThatThrownBy(() -> service.changeRole(1L, 1L, RoleName.USER)).isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(roles);
    }

    @Test
    void promotionRequiresActiveAndVerifiedTarget() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L, RoleName.SUPER_ADMIN, UserStatus.ACTIVE, true)));
        for (User target : new User[] {user(2L, RoleName.USER, UserStatus.INACTIVE, true), user(2L, RoleName.USER, UserStatus.ACTIVE, false)}) {
            when(users.findById(2L)).thenReturn(Optional.of(target));
            assertThatThrownBy(() -> service.changeRole(1L, 2L, RoleName.ADMIN)).isInstanceOf(AccountNotEligibleException.class);
        }
        verifyNoInteractions(roles);
    }

    @Test
    void missingTargetAndInvalidIdAreDifferentFromInsufficientPermission() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L, RoleName.SUPER_ADMIN, UserStatus.ACTIVE, true)));
        assertThatThrownBy(() -> service.changeRole(1L, 99L, RoleName.ADMIN)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.changeRole(1L, 0L, RoleName.ADMIN)).isInstanceOf(InvalidAuthInputException.class);
    }

    private User user(Long id, RoleName role, UserStatus status, boolean verified) {
        return new User(id, "Hoang", "u" + id + "@example.com", "hash-fixture", "0901234567",
                new Role(1L, role), status, verified, null, null);
    }
}
