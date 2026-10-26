package com.example.busbooking.api.dto.vehicle;

import com.example.busbooking.domain.enums.VehicleStatus;
import java.time.LocalDateTime;

public record VehicleResponse(
        Long id,
        OperatorDetails operator,
        String licensePlate,
        String vehicleType,
        int seatCapacity,
        VehicleStatus status,
        LocalDateTime createdAt) {
    public record OperatorDetails(Long id, String name) {}
}
