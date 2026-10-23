package com.example.busbooking.business.command;

public record LoginCommand(String email, String password) {
    @Override
    public String toString() { return "LoginCommand[REDACTED]"; }
}
