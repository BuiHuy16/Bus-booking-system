package com.example.busbooking.business.result;

public record IssuedToken(String value, long expiresInSeconds) {
    public IssuedToken {
        if (value == null || value.isBlank() || expiresInSeconds <= 0) {
            throw new IllegalArgumentException("Invalid issued token");
        }
    }

    @Override
    public String toString() { return "IssuedToken[REDACTED]"; }
}
