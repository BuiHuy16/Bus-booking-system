package com.example.busbooking.security;

import org.springframework.security.authentication.BadCredentialsException;

public final class JwtAuthenticationException extends BadCredentialsException {
    public JwtAuthenticationException() {
        super("Authentication is required");
    }
}
