package com.example.busbooking.api.dto.route;

import com.example.busbooking.api.dto.location.LocationResponse;
import com.example.busbooking.domain.enums.RouteStatus;

public record RouteResponse(
        Long id,
        LocationResponse origin,
        LocationResponse destination,
        int estimatedDurationMinutes,
        RouteStatus status) {
}
