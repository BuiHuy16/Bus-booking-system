package com.example.busbooking.domain.exception;

public class InvalidCredentialsException extends BusinessException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
