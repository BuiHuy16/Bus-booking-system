package com.example.busbooking.domain.exception;

public class DuplicateLocationException extends BusinessException {
    public DuplicateLocationException() {
        super("Location already exists");
    }
}
