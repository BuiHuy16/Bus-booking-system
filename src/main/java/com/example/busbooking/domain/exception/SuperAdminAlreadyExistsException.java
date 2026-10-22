package com.example.busbooking.domain.exception;

public class SuperAdminAlreadyExistsException extends BusinessException {
    public SuperAdminAlreadyExistsException() { super("A super admin already exists"); }
}
