package com.example.busbooking.domain.exception;

public class DuplicateEmailException extends BusinessException {
    public DuplicateEmailException() {
        super("Email is already registered");
    }
}
