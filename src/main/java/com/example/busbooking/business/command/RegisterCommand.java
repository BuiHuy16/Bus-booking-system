package com.example.busbooking.business.command;

public record RegisterCommand(String fullName, String email, String password, String phone) {
    @Override
    public String toString() { return "RegisterCommand[REDACTED]"; }
}
