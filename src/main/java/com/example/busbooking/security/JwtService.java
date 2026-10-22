package com.example.busbooking.security;

import com.example.busbooking.business.port.security.TokenProvider;
import com.example.busbooking.business.result.IssuedToken;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public final class JwtService implements TokenProvider {
    private final SecretKey key;
    private final long ttlSeconds;
    private final String issuer;
    private final Clock clock;
    private final JwtParser parser;

    public JwtService(JwtProperties properties, Clock clock) {
        this.key = properties.validatedSigningKey();
        this.ttlSeconds = properties.getExpirationMs() / 1000;
        this.issuer = properties.getIssuer();
        this.clock = clock;
        var builder = Jwts.parser();
        var algorithms = builder.sig();
        // JJWT 0.12.6 rejects an empty registry immediately; retain HS256 while removing others.
        Jwts.SIG.get().values().stream()
                .filter(algorithm -> !"HS256".equals(algorithm.getId()))
                .forEach(algorithms::remove);
        this.parser = builder
                .verifyWith(key)
                .requireIssuer(issuer)
                .clock(() -> Date.from(clock.instant()))
                .clockSkewSeconds(0)
                .build();
    }

    @Override
    public IssuedToken issue(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("A positive user ID is required");
        }
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        String token = Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(ttlSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, ttlSeconds);
    }

    public VerifiedToken verify(String token) {
        if (token == null || token.isBlank() || token.length() > 8192) {
            throw new JwtAuthenticationException();
        }
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            String subject = claims.getSubject();
            Date issuedAt = claims.getIssuedAt();
            Date expiresAt = claims.getExpiration();
            if (subject == null || !subject.matches("[1-9][0-9]*") || issuedAt == null || expiresAt == null) {
                throw new JwtAuthenticationException();
            }
            long userId = Long.parseLong(subject);
            Instant issued = issuedAt.toInstant();
            Instant expiry = expiresAt.toInstant();
            Instant now = clock.instant();
            // Enforce the exact expiry boundary independently of the parser's Date comparison.
            if (issued.isAfter(now) || !expiry.isAfter(issued) || !now.isBefore(expiry)) {
                throw new JwtAuthenticationException();
            }
            return new VerifiedToken(userId, issued, expiry);
        } catch (JwtException | IllegalArgumentException invalid) {
            // Drop parser details: some library messages include raw claims or token fragments.
            throw new JwtAuthenticationException();
        }
    }
}
