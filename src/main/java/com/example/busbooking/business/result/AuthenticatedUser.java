package com.example.busbooking.business.result;

import com.example.busbooking.domain.enums.RoleName;
import com.example.busbooking.domain.model.User;

public record AuthenticatedUser(Long id, String fullName, String email, String phone,
                                RoleName role, boolean emailVerified) {
    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getFullName(), user.getEmail(),
                user.getPhone(), user.getRole().getName(), user.isEmailVerified());
    }
}
