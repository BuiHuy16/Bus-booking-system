package com.example.busbooking.api.dto.auth;

import com.example.busbooking.api.validation.AuthField;
import com.fasterxml.jackson.annotation.JsonAnySetter;

@io.swagger.v3.oas.annotations.media.Schema(additionalProperties = io.swagger.v3.oas.annotations.media.Schema.AdditionalPropertiesValue.FALSE)
public record VerifyEmailRequest(
        @AuthField(AuthField.Kind.EMAIL) String email,
        @jakarta.validation.constraints.NotNull
        @jakarta.validation.constraints.Pattern(regexp = "[0-9]{6}", message = "Code must contain six digits") String code) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unsupported request field");
    }

    @Override public String toString() { return "VerifyEmailRequest[REDACTED]"; }
}
