package com.example.busbooking.domain.exception;

public class VehicleInUseException extends BusinessException {
    public VehicleInUseException() { super("Vehicle still has scheduled or in-progress trips"); }
}
