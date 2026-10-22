package com.example.busbooking.api.dto.user;

import com.example.busbooking.domain.enums.RoleName;

public record UserResponse(Long id, String fullName, String email, String phone,
                         RoleName role, boolean emailVerified) { }
