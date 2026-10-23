package com.example.busbooking.domain.model;

public record VehicleSummary(
        String vehicleType,
        int seatCapacity,
        String operatorName
) {
}
