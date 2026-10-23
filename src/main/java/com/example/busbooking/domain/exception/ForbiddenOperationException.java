package com.example.busbooking.domain.exception;

public class ForbiddenOperationException extends BusinessException {
    public ForbiddenOperationException() { super("You do not have permission to perform this operation"); }
    public ForbiddenOperationException(String message) { super(message); }
}
