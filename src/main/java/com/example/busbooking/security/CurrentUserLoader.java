package com.example.busbooking.security;

import com.example.busbooking.business.port.repository.UserRepository;
import com.example.busbooking.domain.enums.UserStatus;

public final class CurrentUserLoader {
    private final UserRepository users;

    public CurrentUserLoader(UserRepository users) { this.users = users; }

    public AuthenticatedPrincipal loadById(Long userId) {
        if (userId == null || userId <= 0) { throw new JwtAuthenticationException(); }
        var user = users.findById(userId).orElseThrow(JwtAuthenticationException::new);
        if (user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()
                || user.getRole() == null || user.getRole().getName() == null) {
            throw new JwtAuthenticationException();
        }
        return new AuthenticatedPrincipal(user.getId(), user.getEmail(), user.getRole().getName());
    }
}
