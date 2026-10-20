package com.example.busbooking.api.dto.booking;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BookingRequest(
        @NotNull Long tripId,
        @NotBlank String passengerName,
        @NotBlank String passengerPhone,
        @Min(1) int quantity
) {
}
