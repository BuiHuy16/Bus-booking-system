package com.example.busbooking.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AuthFieldValidator.class)
public @interface AuthField {
    Kind value();
    String message() default "Invalid value";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    enum Kind { EMAIL, FULL_NAME, PHONE, REGISTER_PASSWORD, LOGIN_PASSWORD }
}
