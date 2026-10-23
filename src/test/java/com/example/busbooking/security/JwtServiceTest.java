package com.example.busbooking.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;

import static com.example.busbooking.security.JwtTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {
    private final JwtService jwt = new JwtService(properties(), CLOCK);

    @Test
    void issuesOnlyIdentityAndRequiredClaimsWithTtlInSeconds() {
        var result = jwt.issue(42L);
        assertThat(result.expiresInSeconds()).isEqualTo(3600);
        var verified = jwt.verify(result.value());
        assertThat(verified).isEqualTo(new VerifiedToken(42L, NOW, NOW.plusSeconds(3600)));
        var parsed = Jwts.parser().verifyWith(key("HmacSHA256")).clock(() -> Date.from(NOW))
                .build().parseSignedClaims(result.value());
        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(parsed.getPayload()).containsOnlyKeys("sub", "iat", "exp", "iss");
        assertThat(result.toString()).doesNotContain(result.value());
        assertThat(verified.toString()).doesNotContain(result.value());
    }

    @Test
    void rejectsNonpositiveOrMissingIdAtIssuance() {
        for (Long id : new Long[] {null, 0L, -1L}) {
            assertThatThrownBy(() -> jwt.issue(id)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsExpiredTokensAtTheExactBoundaryWithoutSleep() {
        String token = jwt.issue(42L).value();
        assertThat(new JwtService(properties(), Clock.fixed(NOW.plusSeconds(3599), ZoneOffset.UTC)).verify(token).userId())
                .isEqualTo(42L);
        assertRejectedAt(token, NOW.plusSeconds(3600));
        assertRejectedAt(token, NOW.plusSeconds(3601));
    }

    @Test
    void issuesWholeSecondDatesEvenWithFractionalClock() {
        var fractional = new JwtService(properties(), Clock.fixed(NOW.plusMillis(987), ZoneOffset.UTC));
        var result = fractional.issue(42L);
        assertThat(fractional.verify(result.value()).issuedAt()).isEqualTo(NOW);
        assertThat(fractional.verify(result.value()).expiresAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void rejectsDifferentKeysAndIssuer() {
        String token = jwt.issue(42L).value();
        var differentKey = properties();
        byte[] bytes = new byte[64];
        Arrays.fill(bytes, (byte) 1);
        differentKey.setSecret(Base64.getEncoder().encodeToString(bytes));
        assertThatThrownBy(() -> new JwtService(differentKey, CLOCK).verify(token)).isInstanceOf(JwtAuthenticationException.class);
        var differentIssuer = properties();
        differentIssuer.setIssuer("other-app");
        assertThatThrownBy(() -> new JwtService(differentIssuer, CLOCK).verify(token)).isInstanceOf(JwtAuthenticationException.class);
    }

    @Test
    void onlyAcceptsHs256EvenWhenTheKeyIsLongEnoughForOtherAlgorithms() {
        for (String algorithm : List.of("HmacSHA384", "HmacSHA512")) {
            var builder = builder(validClaims());
            String token = algorithm.endsWith("384")
                    ? builder.signWith(key(algorithm), Jwts.SIG.HS384).compact()
                    : builder.signWith(key(algorithm), Jwts.SIG.HS512).compact();
            assertRejected(token);
        }
        assertRejected(builder(validClaims()).compact());
    }

    @Test
    void requiresAllFourClaims() {
        for (String claim : List.of("sub", "iat", "exp", "iss")) {
            Map<String, Object> claims = validClaims();
            claims.remove(claim);
            assertRejected(sign(claims));
        }
    }

    @Test
    void rejectsNoncanonicalOrOutOfRangeSubjects() {
        for (String subject : List.of("", "0", "-1", "1.5", "abc", "+1", "01", " 1", "9223372036854775808")) {
            var claims = validClaims();
            claims.put("sub", subject);
            assertRejected(sign(claims));
        }
    }

    @Test
    void rejectsFutureIssuedAtAndInvalidDateOrder() {
        var future = validClaims();
        future.put("iat", Date.from(NOW.plusSeconds(1)));
        assertRejected(sign(future));
        var unordered = validClaims();
        unordered.put("iat", Date.from(NOW.plusSeconds(3601)));
        assertRejected(sign(unordered));
        var equal = validClaims();
        equal.put("exp", Date.from(NOW));
        assertRejected(sign(equal));
    }

    @Test
    void rejectsMalformedMissingOrOversizedTokensWithoutLeakingParserDetails() {
        for (String token : new String[] {null, "", " ", "not-a-token", "a.b.c", "a".repeat(8193)}) {
            assertRejected(token);
        }
        String token = jwt.issue(42L).value();
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"999\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + parts[2];
        assertRejected(tampered);
    }

    @Test
    void rejectsWrongClaimTypesAndFutureNotBefore() {
        var claims = validClaims();
        claims.put("iat", "not-a-date");
        String payload = "{\"sub\":\"42\",\"iss\":\"busbooking-test\",\"iat\":\"not-a-date\",\"exp\":1792630800}";
        assertRejected(Jwts.builder().content(payload).signWith(key("HmacSHA256"), Jwts.SIG.HS256).compact());
        var notYetValid = validClaims();
        notYetValid.put("nbf", Date.from(NOW.plusSeconds(60)));
        assertRejected(sign(notYetValid));
    }

    private Map<String, Object> validClaims() {
        var claims = new HashMap<String, Object>();
        claims.put("sub", "42");
        claims.put("iss", "busbooking-test");
        claims.put("iat", Date.from(NOW));
        claims.put("exp", Date.from(NOW.plusSeconds(3600)));
        return claims;
    }

    private io.jsonwebtoken.JwtBuilder builder(Map<String, Object> claims) { return Jwts.builder().claims(claims); }
    private String sign(Map<String, Object> claims) { return builder(claims).signWith(key("HmacSHA256"), Jwts.SIG.HS256).compact(); }

    private void assertRejectedAt(String token, Instant now) {
        assertThatThrownBy(() -> new JwtService(properties(), Clock.fixed(now, ZoneOffset.UTC)).verify(token))
                .isInstanceOf(JwtAuthenticationException.class).hasMessage("Authentication is required");
    }

    private void assertRejected(String token) {
        assertThatThrownBy(() -> jwt.verify(token)).isInstanceOf(JwtAuthenticationException.class)
                .hasMessage("Authentication is required").hasNoCause();
    }
}
