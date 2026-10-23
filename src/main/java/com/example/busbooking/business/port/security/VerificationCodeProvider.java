package com.example.busbooking.business.port.security;

public interface VerificationCodeProvider {
    String generate();
    String digest(String email, String code);
}
