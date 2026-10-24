package com.example.busbooking.business.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SaveTripCommand(Long routeId, Long vehicleId, LocalDateTime departureTime,
                              LocalDateTime arrivalTime, BigDecimal price) {}
