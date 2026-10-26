package com.example.busbooking.api.dto.operator;

import com.example.busbooking.business.command.SaveOperatorCommand;
import com.example.busbooking.domain.enums.OperatorStatus;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record OperatorRequest(
        @NotNull @NotBlank String name,
        @NotNull @NotBlank String address,
        @NotNull @NotBlank String phone,
        @NotNull @NotBlank String email,
        @NotNull OperatorStatus status) {

    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unsupported request field");
    }

    public SaveOperatorCommand toCommand() {
        return new SaveOperatorCommand(name, address, phone, email, status);
    }
}
