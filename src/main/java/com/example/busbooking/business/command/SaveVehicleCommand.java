package com.example.busbooking.business.command;

import com.example.busbooking.domain.enums.VehicleStatus;

public record SaveVehicleCommand(
        Long operatorId,
        String licensePlate,
        String vehicleType,
        Integer seatCapacity,
        VehicleStatus status) {
}
