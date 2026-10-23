package com.example.busbooking.business.service;

import com.example.busbooking.business.command.RegisterCommand;
import com.example.busbooking.business.port.repository.*;
import com.example.busbooking.business.port.security.PasswordHasher;
import com.example.busbooking.domain.enums.*;
import com.example.busbooking.domain.exception.*;
import com.example.busbooking.domain.model.User;

public class BootstrapSuperAdminService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordHasher passwords;
    private final EmailVerificationService verification;

    public BootstrapSuperAdminService(UserRepository users, RoleRepository roles,
            PasswordHasher passwords, EmailVerificationService verification) {
        this.users = users; this.roles = roles; this.passwords = passwords; this.verification = verification;
    }

    public Result bootstrap(RegisterCommand command) {
        if (command == null) { throw new IllegalStateException("Super admin bootstrap configuration is required"); }
        String email = AuthInputPolicy.email(command.email());
        var existing = users.findSuperAdmin();
        if (existing.isPresent()) {
            if (!existing.get().getEmail().equals(email)) {
                throw new IllegalStateException("A super admin already exists; bootstrap cannot replace it");
            }
            return Result.ALREADY_PRESENT;
        }
        if (users.findByEmail(email).isPresent()) {
            throw new IllegalStateException("Bootstrap cannot promote or reset an existing account");
        }
        String name = AuthInputPolicy.fullName(command.fullName());
        String phone = AuthInputPolicy.phone(command.phone());
        AuthInputPolicy.registrationPassword(command.password());
        var role = roles.findByName(RoleName.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("Required SUPER_ADMIN role is missing"));
        User pending = new User(null, name, email, passwords.hash(command.password()), phone,
                role, UserStatus.ACTIVE, false, null, null);
        try {
            verification.register(pending);
            return Result.CREATED_VERIFICATION_SENT;
        } catch (DuplicateEmailException | SuperAdminAlreadyExistsException concurrentCreation) {
            var winner = users.findSuperAdmin();
            if (winner.isPresent() && winner.get().getEmail().equals(email)) { return Result.ALREADY_PRESENT; }
            throw new IllegalStateException("Bootstrap cannot replace a super admin or promote an existing account");
        } catch (EmailDeliveryException unavailable) {
            // The account/challenge already committed. Keep the application usable for resend.
            return Result.CREATED_EMAIL_PENDING;
        }
    }

    public enum Result { CREATED_VERIFICATION_SENT, ALREADY_PRESENT, CREATED_EMAIL_PENDING }
}
