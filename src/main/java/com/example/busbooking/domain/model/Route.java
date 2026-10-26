package com.example.busbooking.domain.model;

import com.example.busbooking.domain.enums.RouteStatus;

public record Route(
        Long id,
        Location origin,
        Location destination,
        int estimatedDurationMinutes,
        RouteStatus status) {
}
