package com.example.busbooking.api.dto.user;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record ChangeRoleRequest(@NotNull ManagedRole role) {
    public enum ManagedRole { USER, ADMIN }
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unsupported request field"); }
}
