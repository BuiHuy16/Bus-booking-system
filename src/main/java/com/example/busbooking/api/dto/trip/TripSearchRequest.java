package com.example.busbooking.api.dto.trip;

import java.time.LocalDate;

public record TripSearchRequest(
        Long originId,
        Long destinationId,
        LocalDate departureDate
) {
}
