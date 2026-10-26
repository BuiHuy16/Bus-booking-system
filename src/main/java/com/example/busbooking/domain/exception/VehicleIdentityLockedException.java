package com.example.busbooking.domain.exception;

public class VehicleIdentityLockedException extends BusinessException {
    public VehicleIdentityLockedException() { super("Vehicle identity cannot be changed after the vehicle has trips"); }
}
