package com.example.busbooking.api.dto.trip;

import com.example.busbooking.domain.enums.TripStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TripResponse(
        Long id,
        Long routeId,
        Long vehicleId,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        BigDecimal price,
        int availableSeats,
        TripStatus status
) {
}
