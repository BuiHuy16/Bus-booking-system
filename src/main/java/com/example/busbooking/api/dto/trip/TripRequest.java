package com.example.busbooking.api.dto.trip;

import com.example.busbooking.business.command.SaveTripCommand;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@io.swagger.v3.oas.annotations.media.Schema(additionalProperties = io.swagger.v3.oas.annotations.media.Schema.AdditionalPropertiesValue.FALSE)
public record TripRequest(
        @NotNull @Positive Long routeId,
        @NotNull @Positive Long vehicleId,
        @NotNull LocalDateTime departureTime,
        @NotNull LocalDateTime arrivalTime,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal price) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unsupported request field");
    }

    public SaveTripCommand toCommand() {
        return new SaveTripCommand(routeId, vehicleId, departureTime, arrivalTime, price);
    }
}
