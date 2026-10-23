package com.example.busbooking.api.dto.trip;

import com.example.busbooking.domain.enums.TripStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TripResponse(
        Long id,
        Long routeId,
        Long vehicleId,
        RouteDetails route,
        VehicleDetails vehicle,
        String origin,
        String destination,
        String vehicleType,
        String operatorName,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        BigDecimal price,
        int availableSeats,
        TripStatus status
) {
    public record RouteDetails(String origin, String destination, int estimatedDurationMinutes) {}
    public record VehicleDetails(String vehicleType, int seatCapacity, String operatorName) {}
}
