package com.example.busbooking.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.*;
import java.util.Base64;

public final class JwtTestSupport {
    public static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");
    public static final String SECRET = Base64.getEncoder().encodeToString(new byte[64]);
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private JwtTestSupport() { }

    public static JwtProperties properties() {
        var properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setIssuer("busbooking-test");
        properties.setExpirationMs(3_600_000);
        return properties;
    }

    public static SecretKey key(String algorithm) {
        return new SecretKeySpec(Base64.getDecoder().decode(SECRET), algorithm);
    }
}
