package com.example.busbooking.business.command;

public record VerifyEmailCommand(String email, String code) {
    @Override
    public String toString() { return "VerifyEmailCommand[REDACTED]"; }
}
