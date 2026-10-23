package com.example.busbooking.data.adapter;

import com.example.busbooking.business.port.repository.EmailVerificationRepository;
import com.example.busbooking.business.port.repository.UserRepository;
import com.example.busbooking.domain.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class EmailVerificationRepositoryAdapter implements EmailVerificationRepository {
    private final JdbcTemplate jdbc;
    private final UserRepository users;

    public EmailVerificationRepositoryAdapter(JdbcTemplate jdbc, UserRepository users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    @Override
    @Transactional
    public User createPending(User user, String digest, Instant now, Instant expiresAt) {
        if (user.getId() != null || user.isEmailVerified()) {
            throw new IllegalArgumentException("A new unverified user is required");
        }
        User saved = users.save(user);
        jdbc.update("""
                INSERT INTO email_verifications(user_id, code_hash, sent_at, expires_at)
                VALUES (?, ?, ?, ?)
                """, saved.getId(), digest, Timestamp.from(now), Timestamp.from(expiresAt));
        return saved;
    }

    @Override
    @Transactional
    public boolean verify(String email, String digest, Clock clock, int maxAttempts) {
        List<Challenge> rows = lock(email);
        if (rows.isEmpty()) { return false; }
        Instant now = clock.instant();
        Challenge challenge = rows.getFirst();
        if (challenge.verified() || !challenge.active() || challenge.digest() == null
                || !now.isBefore(challenge.expiresAt())
                || challenge.attempts() >= maxAttempts) { return false; }
        if (!MessageDigest.isEqual(digest.getBytes(StandardCharsets.US_ASCII),
                challenge.digest().getBytes(StandardCharsets.US_ASCII))) {
            jdbc.update("UPDATE email_verifications SET failed_attempts = failed_attempts + 1 WHERE user_id = ?", challenge.userId());
            return false;
        }
        jdbc.update("UPDATE users SET email_verified = TRUE WHERE id = ?", challenge.userId());
        jdbc.update("DELETE FROM email_verifications WHERE user_id = ?", challenge.userId());
        return true;
    }

    @Override
    @Transactional
    public ResendResult replace(String email, String digest, Instant now, Instant expiresAt, Duration cooldown) {
        List<Challenge> rows = lock(email);
        if (rows.isEmpty()) { return ResendResult.IGNORED; }
        Challenge challenge = rows.getFirst();
        if (challenge.verified() || !challenge.active()) { return ResendResult.IGNORED; }
        if (challenge.sentAt() != null && now.isBefore(challenge.sentAt().plus(cooldown))) {
            return ResendResult.TOO_SOON;
        }
        jdbc.update("""
                INSERT INTO email_verifications(user_id, code_hash, sent_at, expires_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE
                SET code_hash = EXCLUDED.code_hash, sent_at = EXCLUDED.sent_at,
                    expires_at = EXCLUDED.expires_at, failed_attempts = 0
                """, challenge.userId(), digest, Timestamp.from(now), Timestamp.from(expiresAt));
        return ResendResult.REPLACED;
    }

    private List<Challenge> lock(String email) {
        // Lock the account first, then read the challenge in a separate statement.
        // A joined read could retain a stale challenge snapshot while waiting for this lock.
        var accounts = jdbc.query("SELECT id, email_verified, status FROM users WHERE email = ? FOR UPDATE",
                (row, index) -> new Account(row.getLong("id"), row.getBoolean("email_verified"),
                        "ACTIVE".equals(row.getString("status"))), email);
        if (accounts.isEmpty()) { return List.of(); }
        Account account = accounts.getFirst();
        var challenges = jdbc.query("""
                SELECT code_hash, sent_at, expires_at, failed_attempts
                FROM email_verifications WHERE user_id = ?
                """, (row, index) -> new Challenge(account.id(), account.verified(), account.active(),
                row.getString("code_hash"), instant(row.getTimestamp("sent_at")),
                instant(row.getTimestamp("expires_at")), row.getInt("failed_attempts")), account.id());
        return challenges.isEmpty()
                ? List.of(new Challenge(account.id(), account.verified(), account.active(), null, null, null, 0))
                : challenges;
    }

    private record Account(long id, boolean verified, boolean active) { }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record Challenge(long userId, boolean verified, boolean active, String digest,
                             Instant sentAt, Instant expiresAt, int attempts) {
        @Override public String toString() { return "Challenge[REDACTED]"; }
    }
}
