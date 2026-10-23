package com.example.busbooking.domain.model;

public record RouteSummary(
        String origin,
        String destination,
        int estimatedDurationMinutes
) {
}
