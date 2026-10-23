package com.example.busbooking.domain.exception;

public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException() { super("User not found"); }
    public ResourceNotFoundException(String message) { super(message); }
}
