package com.example.busbooking.api.mapper;

import com.example.busbooking.api.dto.auth.*;
import com.example.busbooking.api.dto.user.UserResponse;
import com.example.busbooking.business.command.*;
import com.example.busbooking.business.result.*;
import org.springframework.stereotype.Component;

@Component
public class AuthDtoMapper {
    public RegisterCommand command(RegisterRequest request) {
        return new RegisterCommand(request.fullName(), request.email(), request.password(), request.phone());
    }
    public LoginCommand command(LoginRequest request) { return new LoginCommand(request.email(), request.password()); }
    public VerifyEmailCommand command(VerifyEmailRequest request) { return new VerifyEmailCommand(request.email(), request.code()); }
    public UserResponse user(AuthenticatedUser user) {
        return new UserResponse(user.id(), user.fullName(), user.email(), user.phone(), user.role(), user.emailVerified());
    }
    public CurrentUserResponse currentUser(AuthenticatedUser user) {
        return new CurrentUserResponse(user.id(), user.fullName(), user.email(), user.phone(), user.role(), user.emailVerified());
    }
    public LoginResponse login(LoginResult result) {
        return new LoginResponse(result.token().value(), "Bearer", result.token().expiresInSeconds(), user(result.user()));
    }
}
