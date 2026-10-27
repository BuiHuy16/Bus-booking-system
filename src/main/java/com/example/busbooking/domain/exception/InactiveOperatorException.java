package com.example.busbooking.domain.exception;

public class InactiveOperatorException extends BusinessException {
    public InactiveOperatorException() { super("Operator must be active for this vehicle state"); }
}
