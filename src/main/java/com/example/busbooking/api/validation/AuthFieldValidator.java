package com.example.busbooking.api.validation;

import com.example.busbooking.business.service.AuthInputPolicy;
import com.example.busbooking.domain.exception.InvalidAuthInputException;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class AuthFieldValidator implements ConstraintValidator<AuthField, String> {
    private AuthField.Kind kind;
    @Override public void initialize(AuthField field) { kind = field.value(); }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        try {
            switch (kind) {
                case EMAIL -> AuthInputPolicy.email(value);
                case FULL_NAME -> AuthInputPolicy.fullName(value);
                case PHONE -> AuthInputPolicy.phone(value);
                case REGISTER_PASSWORD -> AuthInputPolicy.registrationPassword(value);
                case LOGIN_PASSWORD -> {
                    if (value == null || value.isEmpty() || value.getBytes(StandardCharsets.UTF_8).length > 72) {
                        throw new InvalidAuthInputException("password", "Password requires 1 to 72 UTF-8 bytes");
                    }
                }
            }
            return true;
        } catch (InvalidAuthInputException invalid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(invalid.getMessage()).addConstraintViolation();
            return false;
        }
    }
}
