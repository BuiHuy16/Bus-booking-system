package com.example.busbooking.api.dto.auth;

import com.example.busbooking.api.validation.AuthField;
import com.fasterxml.jackson.annotation.JsonAnySetter;

@io.swagger.v3.oas.annotations.media.Schema(additionalProperties = io.swagger.v3.oas.annotations.media.Schema.AdditionalPropertiesValue.FALSE)
public record LoginRequest(
        @AuthField(AuthField.Kind.EMAIL) String email,
        @io.swagger.v3.oas.annotations.media.Schema(format = "password", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY)
        @AuthField(AuthField.Kind.LOGIN_PASSWORD) String password) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unsupported request field");
    }

    @Override public String toString() { return "LoginRequest[REDACTED]"; }
}
