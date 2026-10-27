package com.example.busbooking.business.service;

import com.example.busbooking.domain.exception.InvalidOperatorInputException;
import java.util.Locale;
import java.util.regex.Pattern;

final class OperatorInputPolicy {
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@"
                    + "[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+");
    private static final Pattern PHONE = Pattern.compile("0(?:[35789][0-9]{8}|2[0-9]{9})");

    private OperatorInputPolicy() { }

    static String text(String raw, String field, int maxLength) {
        String value = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (value.isEmpty() || value.length() > maxLength || value.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidOperatorInputException(field,
                    field + " must contain between 1 and " + maxLength + " characters");
        }
        return value;
    }

    static String email(String raw) {
        String value = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        int separator = value.indexOf('@');
        if (value.length() > 100 || !EMAIL.matcher(value).matches() || separator > 64) {
            throw new InvalidOperatorInputException("email", "A valid email of at most 100 characters is required");
        }
        return value;
    }

    static String phone(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.startsWith("+84")) value = "0" + value.substring(3);
        if (!PHONE.matcher(value).matches()) {
            throw new InvalidOperatorInputException("phone", "A Vietnamese phone number is required");
        }
        return value;
    }

    static String query(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String value = raw.strip().replaceAll("\\s+", " ");
        if (value.length() > 100) {
            throw new InvalidOperatorInputException("q", "Search query must not exceed 100 characters");
        }
        return value;
    }
}
