package com.example.busbooking.security;

import com.example.busbooking.domain.exception.InvalidAuthInputException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BCryptPasswordHasherTest {
    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher(
            new PasswordEncoderConfig().passwordEncoder());

    @Test
    void hashesWithRandomSaltAndVerifiesCorrectPassword() {
        String first = hasher.hash("Password!123");
        String second = hasher.hash("Password!123");
        assertThat(first).startsWith("$2a$10$").isNotEqualTo(second).isNotEqualTo("Password!123");
        assertThat(hasher.matches("Password!123", first)).isTrue();
        assertThat(hasher.matches("Password!123", second)).isTrue();
        assertThat(hasher.matches("WrongPassword", first)).isFalse();
    }

    @Test
    void preservesWhitespaceAndCaseInPasswords() {
        String hash = hasher.hash(" Password!123 ");
        assertThat(hasher.matches(" Password!123 ", hash)).isTrue();
        assertThat(hasher.matches("Password!123", hash)).isFalse();
        assertThat(hasher.matches(" password!123 ", hash)).isFalse();
    }

    @Test
    void acceptsExactly72Utf8BytesAndRejectsMoreWithoutTruncation() {
        String password = "ắ".repeat(24); // 24 x 3 bytes
        String hash = hasher.hash(password);
        assertThat(hasher.matches(password, hash)).isTrue();
        assertThatThrownBy(() -> hasher.hash(password + "a"))
                .isInstanceOf(InvalidAuthInputException.class);
        assertThat(hasher.matches(password + "a", hash)).isFalse();
        assertThatThrownBy(() -> hasher.hash("a".repeat(73)))
                .isInstanceOf(InvalidAuthInputException.class);
    }

    @Test
    void rejectsMissingPasswordsAndMalformedHashes() {
        assertThatThrownBy(() -> hasher.hash(null)).isInstanceOf(InvalidAuthInputException.class);
        assertThatThrownBy(() -> hasher.hash("")).isInstanceOf(InvalidAuthInputException.class);
        assertThat(hasher.matches(null, "invalid")).isFalse();
        assertThat(hasher.matches("Password!123", null)).isFalse();
        assertThat(hasher.matches("Password!123", "invalid")).isFalse();
    }
}
