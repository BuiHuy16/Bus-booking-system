package com.example.busbooking.api.dto.route;

import com.example.busbooking.business.command.SaveRouteCommand;
import com.example.busbooking.domain.enums.RouteStatus;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record RouteRequest(
        @NotNull @Positive Long originId,
        @NotNull @Positive Long destinationId,
        @NotNull @Positive Integer estimatedDurationMinutes,
        @NotNull RouteStatus status) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unsupported request field");
    }

    public SaveRouteCommand toCommand() {
        return new SaveRouteCommand(originId, destinationId, estimatedDurationMinutes, status);
    }
}
