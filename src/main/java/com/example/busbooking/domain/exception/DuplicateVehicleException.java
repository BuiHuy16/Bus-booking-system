package com.example.busbooking.domain.exception;

public class DuplicateVehicleException extends BusinessException {
    public DuplicateVehicleException() { super("Vehicle license plate already exists"); }
}
