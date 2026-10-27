package com.example.busbooking.domain.model;

import com.example.busbooking.domain.enums.VehicleStatus;
import java.time.LocalDateTime;

public record Vehicle(
        Long id,
        OperatorSummary operator,
        String licensePlate,
        String vehicleType,
        int seatCapacity,
        VehicleStatus status,
        LocalDateTime createdAt) {
}
