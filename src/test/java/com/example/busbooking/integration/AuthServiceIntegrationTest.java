package com.example.busbooking.integration;

import com.example.busbooking.business.command.*;
import com.example.busbooking.business.port.notification.EmailVerificationSender;
import com.example.busbooking.business.port.repository.*;
import com.example.busbooking.business.port.security.*;
import com.example.busbooking.business.result.IssuedToken;
import com.example.busbooking.business.service.*;
import com.example.busbooking.domain.enums.*;
import com.example.busbooking.domain.exception.*;
import com.example.busbooking.domain.model.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceIntegrationTest {
    private static final String EMAIL = "auth.phase1.user@example.com";
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private EmailVerificationRepository verificationRepository;
    @Autowired private PasswordHasher passwords;
    @Autowired private VerificationCodeProvider codes;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    private final MutableClock clock = new MutableClock();
    private final List<Delivery> deliveries = new CopyOnWriteArrayList<>();
    private final AtomicInteger issued = new AtomicInteger();
    private EmailVerificationService verification;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        cleanFixtures();
        clock.now = Instant.parse("2026-10-22T00:00:00Z");
        verification = verification((email, code, expiry) -> deliveries.add(new Delivery(email, code, expiry)));
        auth = auth(users, verification);
    }

    @AfterEach
    void cleanFixtures() {
        jdbc.update("DELETE FROM users WHERE email LIKE 'auth.phase1.%@example.com'");
    }

    @Test
    void registrationSendsOtpAndLoginSucceedsOnlyAfterOneTimeVerification() {
        var registered = auth.register(command(EMAIL));
        assertThat(registered.emailVerified()).isFalse();
        assertThat(deliveries).hasSize(1);
        User stored = users.findByEmail(EMAIL).orElseThrow();
        assertThat(passwords.matches("Password123", stored.getPasswordHash())).isTrue();
        assertThat(stored.getRole().getName()).isEqualTo(RoleName.USER);
        String digest = jdbc.queryForObject("SELECT code_hash FROM email_verifications WHERE user_id = ?", String.class, stored.getId());
        assertThat(digest).matches("[a-f0-9]{64}").isNotEqualTo(deliveries.getFirst().code());
        assertThatThrownBy(() -> auth.login(login())).isInstanceOf(InvalidCredentialsException.class);
        assertThat(issued).hasValue(0);
        verifyLatest();
        assertThat(users.findByEmail(EMAIL).orElseThrow().isEmailVerified()).isTrue();
        assertThat(auth.login(login()).user().emailVerified()).isTrue();
        assertThat(issued).hasValue(1);
        assertThatThrownBy(this::verifyLatest).isInstanceOf(InvalidVerificationCodeException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_verifications WHERE user_id = ?", Long.class, stored.getId())).isZero();
    }

    @Test
    void wrongAttemptsPersistAndFiveFailuresBlockEvenCorrectCode() {
        auth.register(command(EMAIL));
        String code = deliveries.getFirst().code();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThatThrownBy(() -> verification.verify(new VerifyEmailCommand(EMAIL, wrong)))
                    .isInstanceOf(InvalidVerificationCodeException.class);
            assertThat(jdbc.queryForObject("SELECT failed_attempts FROM email_verifications WHERE user_id = ?",
                    Integer.class, users.findByEmail(EMAIL).orElseThrow().getId())).isEqualTo(attempt);
        }
        assertThatThrownBy(this::verifyLatest).isInstanceOf(InvalidVerificationCodeException.class);
        assertThat(users.findByEmail(EMAIL).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    void expiresExactlyAtTenMinutesWithoutSleeping() {
        auth.register(command(EMAIL));
        clock.advance(Duration.ofMinutes(10));
        assertThatThrownBy(this::verifyLatest).isInstanceOf(InvalidVerificationCodeException.class);
    }

    @Test
    void rejectsOtpThatExpiresWhileVerificationWaitsForTheAccountLock() throws Exception {
        auth.register(command(EMAIL));
        long userId = users.findByEmail(EMAIL).orElseThrow().getId();
        var executor = Executors.newSingleThreadExecutor();
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            int lockerPid;
            try (var query = connection.createStatement(); var result = query.executeQuery("SELECT pg_backend_pid()")) {
                result.next();
                lockerPid = result.getInt(1);
            }
            try (var query = connection.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE")) {
                query.setLong(1, userId);
                query.executeQuery().close();
            }
            Future<?> attempt = executor.submit(this::verifyLatest);
            // Observe a real database lock wait before advancing time; no timing-based sleep.
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> jdbc.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))",
                    Long.class, lockerPid) > 0);
            clock.advance(Duration.ofMinutes(10));
            connection.commit();
            assertThatThrownBy(() -> attempt.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(InvalidVerificationCodeException.class);
            assertThat(users.findById(userId).orElseThrow().isEmailVerified()).isFalse();
            assertThat(jdbc.queryForObject("SELECT failed_attempts FROM email_verifications WHERE user_id = ?",
                    Integer.class, userId)).isZero();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void resendingHasCooldownAndInvalidatesPreviousChallenge() {
        auth.register(command(EMAIL));
        String oldCode = deliveries.getFirst().code();
        assertThatThrownBy(() -> verification.resend(EMAIL)).isInstanceOf(VerificationRateLimitException.class);
        assertThat(deliveries).hasSize(1);
        clock.advance(Duration.ofSeconds(60));
        // Deterministic replacement guarantees this test never flakes on a random OTP collision.
        String replacement = oldCode.equals("000000") ? "111111" : "000000";
        VerificationCodeProvider replacementCodes = new VerificationCodeProvider() {
            public String generate() { return replacement; }
            public String digest(String email, String code) { return codes.digest(email, code); }
        };
        var resendService = new EmailVerificationService(verificationRepository, replacementCodes,
                (email, code, expiry) -> deliveries.add(new Delivery(email, code, expiry)), clock);
        resendService.resend(" AUTH.PHASE1.USER@EXAMPLE.COM ");
        assertThat(deliveries).hasSize(2);
        assertThatThrownBy(() -> verification.verify(new VerifyEmailCommand(EMAIL, oldCode)))
                .isInstanceOf(InvalidVerificationCodeException.class);
        verifyLatest();
    }

    @Test
    void resendingAfterLockoutRestoresAttemptsButNotBeforeCooldown() {
        auth.register(command(EMAIL));
        String wrong = deliveries.getFirst().code().equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> verification.verify(new VerifyEmailCommand(EMAIL, wrong)))
                    .isInstanceOf(InvalidVerificationCodeException.class);
        }
        clock.advance(Duration.ofSeconds(60));
        verification.resend(EMAIL);
        verifyLatest();
        assertThat(users.findByEmail(EMAIL).orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    void verifiedUnknownAndInactiveAccountsDoNotReceiveResendMail() {
        verification.resend("auth.phase1.missing@example.com");
        auth.register(command(EMAIL));
        verifyLatest();
        clock.advance(Duration.ofMinutes(1));
        verification.resend(EMAIL);
        assertThat(deliveries).hasSize(1);
        jdbc.update("UPDATE users SET status = 'INACTIVE' WHERE email = ?", EMAIL);
        assertThatThrownBy(() -> auth.login(login())).isInstanceOf(InvalidCredentialsException.class);
        assertThat(issued).hasValue(0);
    }

    @Test
    void inactivePendingUserCannotVerifyOrReceiveResend() {
        auth.register(command(EMAIL));
        jdbc.update("UPDATE users SET status = 'INACTIVE' WHERE email = ?", EMAIL);
        assertThatThrownBy(this::verifyLatest).isInstanceOf(InvalidVerificationCodeException.class);
        clock.advance(Duration.ofMinutes(1));
        verification.resend(EMAIL);
        assertThat(deliveries).hasSize(1);
    }

    @Test
    void legacyUnverifiedUserCanRequestFirstChallengeAfterMigration() {
        users.save(new User(null, "Hoang", EMAIL, passwords.hash("Password123"), "0901234567",
                roles.findByName(RoleName.USER).orElseThrow(), UserStatus.ACTIVE, null, null));
        assertThatThrownBy(() -> verification.verify(new VerifyEmailCommand(EMAIL, "012345")))
                .isInstanceOf(InvalidVerificationCodeException.class);
        verification.resend(EMAIL);
        assertThat(deliveries).hasSize(1);
        verifyLatest();
        assertThat(auth.login(login()).user().emailVerified()).isTrue();
    }

    @Test
    void concurrentResendSendsOnlyOneEmailAndCooldownIsAtomic() throws Exception {
        auth.register(command(EMAIL));
        clock.advance(Duration.ofSeconds(60));
        CyclicBarrier start = new CyclicBarrier(2);
        var outcomes = parallel(() -> {
            await(start);
            try { verification.resend(EMAIL); return "sent"; }
            catch (VerificationRateLimitException limited) { return "limited"; }
        });
        assertThat(outcomes).containsExactlyInAnyOrder("sent", "limited");
        assertThat(deliveries).hasSize(2);
    }

    @Test
    void smtpFailureKeepsRecoverablePendingAccountAndResendWorks() {
        var failedDelivery = verification((email, code, expiry) -> { throw new EmailDeliveryException(); });
        assertThatThrownBy(() -> auth(users, failedDelivery).register(command(EMAIL)))
                .isInstanceOf(EmailDeliveryException.class);
        assertThat(users.findByEmail(EMAIL)).isPresent();
        assertThatThrownBy(() -> auth.login(login())).isInstanceOf(InvalidCredentialsException.class);
        clock.advance(Duration.ofSeconds(60));
        verification.resend(EMAIL);
        verifyLatest();
        assertThat(auth.login(login()).user().emailVerified()).isTrue();
    }

    @Test
    void accountAndChallengeAreCreatedAtomically() {
        User pending = new User(null, "Hoang", EMAIL, passwords.hash("Password123"), "0901234567",
                roles.findByName(RoleName.USER).orElseThrow(), UserStatus.ACTIVE, false, null, null);
        assertThatThrownBy(() -> verificationRepository.createPending(pending, "invalid-hash",
                clock.instant(), clock.instant().plusSeconds(600)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(users.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    void concurrentRegistrationCreatesExactlyOneAccountAndOneMail() throws Exception {
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        UserRepository synchronizedUsers = new UserRepository() {
            public Optional<User> findById(Long id) { return users.findById(id); }
            public Optional<User> findByEmail(String email) { return users.findByEmail(email); }
            public User save(User user) { return users.save(user); }
            public Optional<User> updateProfile(Long id, String name, String phone) { return users.updateProfile(id, name, phone); }
            public Optional<User> findSuperAdmin() { return users.findSuperAdmin(); }
            public boolean existsByEmail(String email) {
                boolean result = users.existsByEmail(email);
                await(bothChecked);
                return result;
            }
        };
        AuthService concurrent = auth(synchronizedUsers, verification);
        List<String> outcomes = parallel(() -> {
            try { concurrent.register(command(EMAIL)); return "created"; }
            catch (DuplicateEmailException duplicate) { return "duplicate"; }
        });
        assertThat(outcomes).containsExactlyInAnyOrder("created", "duplicate");
        assertThat(deliveries).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Long.class, EMAIL)).isEqualTo(1L);
    }

    @Test
    void concurrentVerificationConsumesCodeOnlyOnce() throws Exception {
        auth.register(command(EMAIL));
        CyclicBarrier start = new CyclicBarrier(2);
        var outcomes = parallel(() -> {
            await(start);
            try { verifyLatest(); return "verified"; }
            catch (InvalidVerificationCodeException rejected) { return "rejected"; }
        });
        assertThat(outcomes).containsExactlyInAnyOrder("verified", "rejected");
    }

    private AuthService auth(UserRepository repository, EmailVerificationService verifier) {
        return new AuthService(repository, roles, passwords, id -> {
            issued.incrementAndGet();
            return new IssuedToken("unit-test-only-token", 3600);
        }, verifier);
    }

    private EmailVerificationService verification(EmailVerificationSender sender) {
        return new EmailVerificationService(verificationRepository, codes, sender, clock);
    }

    private RegisterCommand command(String email) {
        return new RegisterCommand("Nguyễn Văn Huy Hoàng", email, "Password123", "+84901234567");
    }

    private LoginCommand login() { return new LoginCommand(EMAIL, "Password123"); }

    private void verifyLatest() {
        verification.verify(new VerifyEmailCommand(EMAIL, deliveries.getLast().code()));
    }

    private static void await(CyclicBarrier barrier) {
        try { barrier.await(5, TimeUnit.SECONDS); }
        catch (Exception error) { throw new IllegalStateException("Concurrent test barrier failed", error); }
    }

    private static List<String> parallel(Callable<String> operation) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(operation);
            Future<String> second = executor.submit(operation);
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test executor did not terminate");
            }
        }
    }

    private record Delivery(String email, String code, Instant expiresAt) {
        @Override public String toString() { return "Delivery[REDACTED]"; }
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
        void advance(Duration duration) { now = now.plus(duration); }
    }
}
