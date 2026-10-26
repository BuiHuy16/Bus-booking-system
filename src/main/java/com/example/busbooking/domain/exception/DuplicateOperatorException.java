package com.example.busbooking.domain.exception;

public class DuplicateOperatorException extends BusinessException {
    public DuplicateOperatorException() {
        super("Operator name or email already exists");
    }
}
