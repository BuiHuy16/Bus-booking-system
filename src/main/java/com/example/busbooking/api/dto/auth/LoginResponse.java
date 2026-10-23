package com.example.busbooking.api.dto.auth;

import com.example.busbooking.api.dto.user.UserResponse;

public record LoginResponse(String token, String tokenType, long expiresIn, UserResponse user) {
    @Override public String toString() { return "LoginResponse[REDACTED]"; }
}
