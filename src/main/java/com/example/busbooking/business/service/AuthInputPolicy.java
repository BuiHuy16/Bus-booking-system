package com.example.busbooking.business.service;

import com.example.busbooking.domain.exception.InvalidAuthInputException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AuthInputPolicy {
    // Deliberately a bounded ASCII email format for this application, not full RFC parsing.
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+");
    private static final Pattern PHONE = Pattern.compile("0(?:[35789][0-9]{8}|2[0-9]{9})");

    private AuthInputPolicy() { }

    public static String email(String raw) {
        String email = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (email.length() > 100 || !EMAIL.matcher(email).matches()
                || email.substring(0, email.indexOf('@')).length() > 64) {
            throw new InvalidAuthInputException("email", "A valid email of at most 100 characters is required");
        }
        return email;
    }

    public static String fullName(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.isBlank() || name.length() > 100 || name.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidAuthInputException("fullName", "Full name must contain 1 to 100 characters");
        }
        return name;
    }

    public static String phone(String raw) {
        String phone = raw == null ? "" : raw.strip();
        if (phone.startsWith("+84")) { phone = "0" + phone.substring(3); }
        if (!PHONE.matcher(phone).matches()) {
            throw new InvalidAuthInputException("phone", "A Vietnamese phone number is required");
        }
        return phone;
    }

    public static void registrationPassword(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 8
                || password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidAuthInputException("password", "Password requires at least 8 characters and at most 72 UTF-8 bytes");
        }
    }
}
