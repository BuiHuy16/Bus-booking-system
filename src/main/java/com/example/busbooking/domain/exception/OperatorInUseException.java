package com.example.busbooking.domain.exception;

public class OperatorInUseException extends BusinessException {
    public OperatorInUseException() {
        super("Operator still has operational vehicles or trips");
    }
}
