package com.example.busbooking.domain.exception;

public class InvalidAuthInputException extends BusinessException {
    private final String field;

    public InvalidAuthInputException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() { return field; }
}
