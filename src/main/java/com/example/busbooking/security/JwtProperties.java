package com.example.busbooking.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String secret;
    private long expirationMs = 86_400_000;
    private String issuer = "busbooking";

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public long getExpirationMs() { return expirationMs; }
    public void setExpirationMs(long expirationMs) { this.expirationMs = expirationMs; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public SecretKey validatedSigningKey() {
        // Validate outside property binding so a rejected secret is never rendered in a binding error.
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(secret); }
        catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IllegalStateException("JWT_SECRET must be valid Base64");
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("JWT_SECRET requires at least 32 decoded bytes");
        }
        if (expirationMs < 1000 || expirationMs % 1000 != 0) {
            throw new IllegalStateException("JWT_EXPIRATION_MS must be positive whole seconds in milliseconds");
        }
        if (issuer == null || issuer.isBlank() || !issuer.equals(issuer.strip())) {
            throw new IllegalStateException("JWT_ISSUER must be nonblank without surrounding whitespace");
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Override
    public String toString() { return "JwtProperties[REDACTED]"; }
}
