package com.example.busbooking.business.result;

public record LoginResult(IssuedToken token, AuthenticatedUser user) {
    @Override
    public String toString() { return "LoginResult[REDACTED]"; }
}
