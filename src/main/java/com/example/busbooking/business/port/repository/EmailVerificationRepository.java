package com.example.busbooking.business.port.repository;

import com.example.busbooking.domain.model.User;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;

public interface EmailVerificationRepository {
    User createPending(User user, String digest, Instant now, Instant expiresAt);
    // Read the clock after acquiring the account lock, not before waiting for it.
    boolean verify(String email, String digest, Clock clock, int maxAttempts);
    ResendResult replace(String email, String digest, Instant now, Instant expiresAt, Duration cooldown);

    enum ResendResult { REPLACED, IGNORED, TOO_SOON }
}
