package com.example.busbooking.domain.exception;

public class VerificationRateLimitException extends BusinessException {
    public VerificationRateLimitException() { super("Please wait 60 seconds before requesting another code"); }
}
