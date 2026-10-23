package com.example.busbooking.business.service;

import com.example.busbooking.business.port.repository.RoleManagementRepository;
import com.example.busbooking.business.port.repository.UserRepository;
import com.example.busbooking.business.result.AuthenticatedUser;
import com.example.busbooking.domain.enums.*;
import com.example.busbooking.domain.exception.*;
import com.example.busbooking.domain.model.User;

public class RoleManagementService {
    private final UserRepository users;
    private final RoleManagementRepository roles;
    public RoleManagementService(UserRepository users, RoleManagementRepository roles) {
        this.users = users; this.roles = roles;
    }

    public AuthenticatedUser changeRole(Long actorId, Long targetId, RoleName role) {
        if (actorId == null || actorId <= 0) { throw new InvalidCredentialsException(); }
        User actor = users.findById(actorId).orElseThrow(InvalidCredentialsException::new);
        if (actor.getStatus() != UserStatus.ACTIVE || !actor.isEmailVerified()) { throw new InvalidCredentialsException(); }
        if (actor.getRole().getName() != RoleName.SUPER_ADMIN) { throw new ForbiddenOperationException(); }
        if (targetId == null || targetId <= 0) { throw new InvalidAuthInputException("userId", "A positive user ID is required"); }
        if (role != RoleName.USER && role != RoleName.ADMIN) {
            throw new InvalidAuthInputException("role", "Only USER or ADMIN can be assigned");
        }
        User target = users.findById(targetId).orElseThrow(ResourceNotFoundException::new);
        if (target.getRole().getName() == RoleName.SUPER_ADMIN) { throw new ForbiddenOperationException(); }
        if (role == RoleName.ADMIN && (target.getStatus() != UserStatus.ACTIVE || !target.isEmailVerified())) {
            throw new AccountNotEligibleException();
        }
        // Adapter rechecks both accounts with row locks before changing only role_id.
        return AuthenticatedUser.from(roles.changeRole(actorId, targetId, role));
    }
}
