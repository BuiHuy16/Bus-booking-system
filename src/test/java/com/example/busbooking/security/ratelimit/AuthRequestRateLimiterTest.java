package com.example.busbooking.security.ratelimit;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class AuthRequestRateLimiterTest {
    private static final String LOGIN = "/api/auth/login";

    @Test
    void enforcesQuotaPerIpAndEndpointAndResetsAtWindowBoundary() {
        var clock = new MutableClock();
        var props = new AuthRateLimitProperties();
        props.setLoginLimit(2);
        var limiter = new AuthRequestRateLimiter(props, clock);
        assertThat(limiter.consume("192.0.2.1", LOGIN).allowed()).isTrue();
        assertThat(limiter.consume("192.0.2.1", LOGIN).allowed()).isTrue();
        var denied = limiter.consume("192.0.2.1", LOGIN);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isEqualTo(60);
        assertThat(limiter.consume("192.0.2.2", LOGIN).allowed()).isTrue();
        assertThat(limiter.consume("192.0.2.1", "/api/auth/register").allowed()).isTrue();
        clock.advance(Duration.ofSeconds(59));
        assertThat(limiter.consume("192.0.2.1", LOGIN).retryAfterSeconds()).isEqualTo(1);
        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.consume("192.0.2.1", LOGIN).allowed()).isTrue();
    }

    @Test
    void capacityIsBoundedAndFailsClosedUntilCleanup() {
        var props = new AuthRateLimitProperties();
        props.setMaxKeys(2);
        var clock = new MutableClock();
        var limiter = new AuthRequestRateLimiter(props, clock);
        assertThat(limiter.consume("ip1", LOGIN).allowed()).isTrue();
        assertThat(limiter.consume("ip2", LOGIN).allowed()).isTrue();
        assertThat(limiter.consume("ip3", LOGIN).allowed()).isFalse();
        assertThat(limiter.consume("ip1", "/api/auth/me").allowed()).isTrue();
        clock.advance(Duration.ofSeconds(60));
        assertThat(limiter.consume("ip3", LOGIN).allowed()).isTrue();
    }

    @Test
    void concurrentRequestsNeverExceedTheQuota() throws Exception {
        var props = new AuthRateLimitProperties();
        props.setLoginLimit(5);
        var limiter = new AuthRequestRateLimiter(props, new MutableClock());
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> requests = new ArrayList<>();
            for (int i = 0; i < 40; i++) { requests.add(() -> limiter.consume("same-ip", LOGIN).allowed()); }
            long allowed = 0;
            for (var future : executor.invokeAll(requests, 5, TimeUnit.SECONDS)) {
                if (future.get(1, TimeUnit.SECONDS)) { allowed++; }
            }
            assertThat(allowed).isEqualTo(5);
        } finally { executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS); }
    }

    @Test
    void rejectsInvalidConfigurationInsteadOfDisablingProtection() {
        var props = new AuthRateLimitProperties(); props.setLoginLimit(0);
        assertThatThrownBy(() -> new AuthRequestRateLimiter(props, new MutableClock())).isInstanceOf(IllegalStateException.class);
        props.setLoginLimit(2); props.setMaxKeys(0);
        assertThatThrownBy(() -> new AuthRequestRateLimiter(props, new MutableClock())).isInstanceOf(IllegalStateException.class);
        props.setMaxKeys(10); props.setWindowSeconds(-1);
        assertThatThrownBy(() -> new AuthRequestRateLimiter(props, new MutableClock())).isInstanceOf(IllegalStateException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-22T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
        void advance(Duration amount) { now = now.plus(amount); }
    }
}
