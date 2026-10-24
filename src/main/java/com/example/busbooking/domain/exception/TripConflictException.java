package com.example.busbooking.domain.exception;

public class TripConflictException extends RuntimeException {
    public TripConflictException(String message) {
        super(message);
    }
}
