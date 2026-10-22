package com.example.busbooking.business.service;

import com.example.busbooking.business.command.VerifyEmailCommand;
import com.example.busbooking.business.port.notification.EmailVerificationSender;
import com.example.busbooking.business.port.repository.EmailVerificationRepository;
import com.example.busbooking.business.port.security.VerificationCodeProvider;
import com.example.busbooking.domain.exception.InvalidVerificationCodeException;
import com.example.busbooking.domain.exception.VerificationRateLimitException;
import com.example.busbooking.domain.model.User;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class EmailVerificationService {
    public static final Duration CODE_TTL = Duration.ofMinutes(10);
    public static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    public static final int MAX_ATTEMPTS = 5;

    private final EmailVerificationRepository repository;
    private final VerificationCodeProvider codes;
    private final EmailVerificationSender sender;
    private final Clock clock;

    public EmailVerificationService(EmailVerificationRepository repository, VerificationCodeProvider codes,
                                    EmailVerificationSender sender, Clock clock) {
        this.repository = repository;
        this.codes = codes;
        this.sender = sender;
        this.clock = clock;
    }

    public User register(User pendingUser) {
        String code = codes.generate();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(CODE_TTL);
        // Account and challenge commit together. SMTP runs after that transaction completes.
        User saved = repository.createPending(pendingUser, codes.digest(pendingUser.getEmail(), code), now, expiresAt);
        sender.send(saved.getEmail(), code, expiresAt);
        return saved;
    }

    public void verify(VerifyEmailCommand command) {
        if (command == null || command.code() == null || !command.code().matches("[0-9]{6}")) {
            throw new InvalidVerificationCodeException();
        }
        String email = AuthInputPolicy.email(command.email());
        // Repository returns an outcome so incorrect-attempt increments commit before throwing.
        if (!repository.verify(email, codes.digest(email, command.code()), clock, MAX_ATTEMPTS)) {
            throw new InvalidVerificationCodeException();
        }
    }

    public void resend(String rawEmail) {
        String email = AuthInputPolicy.email(rawEmail);
        Instant now = clock.instant();
        String code = codes.generate();
        Instant expiresAt = now.plus(CODE_TTL);
        var result = repository.replace(email, codes.digest(email, code), now, expiresAt, RESEND_COOLDOWN);
        if (result == EmailVerificationRepository.ResendResult.TOO_SOON) {
            throw new VerificationRateLimitException();
        }
        if (result == EmailVerificationRepository.ResendResult.REPLACED) {
            sender.send(email, code, expiresAt);
        }
        // Unknown/verified/inactive accounts receive the same successful outward response.
    }
}
