package com.example.busbooking.business.command;

import com.example.busbooking.domain.enums.RouteStatus;

public record SaveRouteCommand(
        Long originId,
        Long destinationId,
        Integer estimatedDurationMinutes,
        RouteStatus status) {
}
