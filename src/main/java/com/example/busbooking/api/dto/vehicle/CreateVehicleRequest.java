package com.example.busbooking.api.dto.vehicle;

import com.example.busbooking.business.command.SaveVehicleCommand;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record CreateVehicleRequest(
        @NotNull @Positive Long operatorId,
        @NotNull @NotBlank String licensePlate,
        @NotNull @NotBlank String vehicleType,
        @NotNull @Positive Integer seatCapacity) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unsupported request field"); }
    public SaveVehicleCommand toCommand() {
        return new SaveVehicleCommand(operatorId, licensePlate, vehicleType, seatCapacity, null);
    }
}
