package com.example.busbooking.domain.exception;

public class AccountNotEligibleException extends BusinessException {
    public AccountNotEligibleException() { super("Target account must be active and email-verified before promotion"); }
}
