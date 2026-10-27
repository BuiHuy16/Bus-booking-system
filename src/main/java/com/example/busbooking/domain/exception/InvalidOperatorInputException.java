package com.example.busbooking.domain.exception;

public class InvalidOperatorInputException extends BusinessException {
    private final String field;

    public InvalidOperatorInputException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() { return field; }
}
