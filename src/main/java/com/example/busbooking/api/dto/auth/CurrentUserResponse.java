package com.example.busbooking.api.dto.auth;

import com.example.busbooking.domain.enums.RoleName;

public record CurrentUserResponse(Long id, String fullName, String email, String phone,
                         RoleName role, boolean emailVerified) { }
