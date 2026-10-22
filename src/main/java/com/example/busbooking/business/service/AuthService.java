package com.example.busbooking.business.service;

import com.example.busbooking.business.command.LoginCommand;
import com.example.busbooking.business.command.RegisterCommand;
import com.example.busbooking.business.port.repository.RoleRepository;
import com.example.busbooking.business.port.repository.UserRepository;
import com.example.busbooking.business.port.security.PasswordHasher;
import com.example.busbooking.business.port.security.TokenProvider;
import com.example.busbooking.business.result.AuthenticatedUser;
import com.example.busbooking.business.result.LoginResult;
import com.example.busbooking.domain.enums.RoleName;
import com.example.busbooking.domain.enums.UserStatus;
import com.example.busbooking.domain.exception.DuplicateEmailException;
import com.example.busbooking.domain.exception.InvalidAuthInputException;
import com.example.busbooking.domain.exception.InvalidCredentialsException;
import com.example.busbooking.domain.model.User;

public class AuthService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordHasher passwords;
    private final TokenProvider tokens;
    private final EmailVerificationService verification;
    private final String dummyHash;

    public AuthService(UserRepository users, RoleRepository roles, PasswordHasher passwords,
                       TokenProvider tokens, EmailVerificationService verification) {
        this.users = users;
        this.roles = roles;
        this.passwords = passwords;
        this.tokens = tokens;
        this.verification = verification;
        this.dummyHash = passwords.hash("dummy-password-comparison-only");
    }

    public AuthenticatedUser register(RegisterCommand command) {
        if (command == null) { throw new InvalidAuthInputException("request", "Registration data is required"); }
        String email = AuthInputPolicy.email(command.email());
        String name = AuthInputPolicy.fullName(command.fullName());
        String phone = AuthInputPolicy.phone(command.phone());
        AuthInputPolicy.registrationPassword(command.password());
        if (users.existsByEmail(email)) { throw new DuplicateEmailException(); }
        var role = roles.findByName(RoleName.USER)
                .orElseThrow(() -> new IllegalStateException("Required USER role is missing"));
        User pending = new User(null, name, email, passwords.hash(command.password()), phone,
                role, UserStatus.ACTIVE, false, null, null);
        return AuthenticatedUser.from(verification.register(pending));
    }

    public AuthenticatedUser currentUser(Long userId) {
        if (userId == null || userId <= 0) { throw new InvalidCredentialsException(); }
        User user = users.findById(userId).orElseThrow(InvalidCredentialsException::new);
        if (user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()
                || user.getRole() == null || user.getRole().getName() == null) {
            throw new InvalidCredentialsException();
        }
        return AuthenticatedUser.from(user);
    }

    public LoginResult login(LoginCommand command) {
        if (command == null) { throw new InvalidCredentialsException(); }
        String email;
        try { email = AuthInputPolicy.email(command.email()); }
        catch (InvalidAuthInputException invalid) { throw new InvalidCredentialsException(); }
        var user = users.findByEmail(email);
        boolean matches = passwords.matches(command.password(), user.map(User::getPasswordHash).orElse(dummyHash));
        if (!matches || user.isEmpty() || user.get().getStatus() != UserStatus.ACTIVE
                || !user.get().isEmailVerified()) {
            throw new InvalidCredentialsException();
        }
        User authenticated = user.get();
        return new LoginResult(tokens.issue(authenticated.getId()), AuthenticatedUser.from(authenticated));
    }
}
