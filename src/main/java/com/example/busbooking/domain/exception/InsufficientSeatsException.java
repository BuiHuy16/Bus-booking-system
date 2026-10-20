package com.example.busbooking.domain.exception;

public class InsufficientSeatsException extends BusinessException {
    public InsufficientSeatsException(String message) {
        super(message);
    }
}
