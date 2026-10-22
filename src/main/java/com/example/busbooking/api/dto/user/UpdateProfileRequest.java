package com.example.busbooking.api.dto.user;

import com.example.busbooking.api.validation.AuthField;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record UpdateProfileRequest(@AuthField(AuthField.Kind.FULL_NAME) String fullName,
                                   @AuthField(AuthField.Kind.PHONE) String phone) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unsupported request field"); }
}
