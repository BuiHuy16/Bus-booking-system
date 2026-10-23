package com.example.busbooking.domain.exception;

public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException() { super("Account is pending verification; email delivery failed, request a new code later"); }
}
