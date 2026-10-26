package com.example.busbooking.api.dto.location;

import com.example.busbooking.business.command.SaveLocationCommand;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record LocationRequest(
        @NotNull @NotBlank String name,
        @NotNull @NotBlank String province,
        @NotNull @NotBlank String address) {

    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unsupported request field");
    }

    public SaveLocationCommand toCommand() {
        return new SaveLocationCommand(name, province, address);
    }
}
