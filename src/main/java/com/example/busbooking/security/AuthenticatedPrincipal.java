package com.example.busbooking.security;

import com.example.busbooking.domain.enums.RoleName;
import java.security.Principal;

public record AuthenticatedPrincipal(Long userId, String email, RoleName role) implements Principal {
    @Override
    public String getName() { return userId.toString(); }
}
