package com.example.busbooking.security;

import com.example.busbooking.business.port.security.PasswordHasher;
import com.example.busbooking.domain.exception.InvalidAuthInputException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Component
public class BCryptPasswordHasher implements PasswordHasher {
    private static final Pattern BCRYPT_HASH = Pattern.compile("\\A\\$2[aby]\\$(0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}\\z");
    private final PasswordEncoder encoder;

    public BCryptPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hash(String rawPassword) {
        if (!validLength(rawPassword)) {
            throw new InvalidAuthInputException("password", "Password must contain 1 to 72 UTF-8 bytes");
        }
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        // Reject malformed hashes before passing them to an encoder that may log the hash.
        if (!validLength(rawPassword) || passwordHash == null || !BCRYPT_HASH.matcher(passwordHash).matches()) {
            return false;
        }
        return encoder.matches(rawPassword, passwordHash);
    }

    private boolean validLength(String value) {
        return value != null && !value.isEmpty()
                && value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
