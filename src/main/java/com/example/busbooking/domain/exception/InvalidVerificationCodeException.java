package com.example.busbooking.domain.exception;

public class InvalidVerificationCodeException extends BusinessException {
    public InvalidVerificationCodeException() { super("Verification code is invalid or expired"); }
}
