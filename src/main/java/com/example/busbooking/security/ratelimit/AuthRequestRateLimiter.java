package com.example.busbooking.security.ratelimit;

import java.time.*;
import java.util.*;

public final class AuthRequestRateLimiter {
    private final AuthRateLimitProperties properties;
    private final Clock clock;
    private final Map<Key, Window> windows = new HashMap<>();
    private Instant nextCleanup = Instant.MIN;

    public AuthRequestRateLimiter(AuthRateLimitProperties properties, Clock clock) {
        properties.validate();
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized Decision consume(String remoteAddress, String path) {
        int quota = properties.quota(path);
        if (quota == 0) { return new Decision(true, 0); }
        Instant now = clock.instant();
        if (!now.isBefore(nextCleanup)) {
            windows.values().removeIf(window -> !now.isBefore(window.endsAt));
            nextCleanup = now.plusSeconds(properties.getWindowSeconds());
        }
        Key key = new Key(Objects.requireNonNullElse(remoteAddress, "unknown"), path);
        Window window = windows.get(key);
        if (window != null && !now.isBefore(window.endsAt)) {
            windows.remove(key);
            window = null;
        }
        if (window == null) {
            if (windows.size() >= properties.getMaxKeys()) {
                return new Decision(false, retryAfter(now, nextCleanup));
            }
            windows.put(key, new Window(now.plusSeconds(properties.getWindowSeconds())));
            return new Decision(true, 0);
        }
        if (window.count >= quota) { return new Decision(false, retryAfter(now, window.endsAt)); }
        window.count++;
        return new Decision(true, 0);
    }

    private long retryAfter(Instant now, Instant end) {
        return Math.max(1, (Duration.between(now, end).toMillis() + 999) / 1000);
    }

    public record Decision(boolean allowed, long retryAfterSeconds) { }
    private record Key(String address, String path) { }
    private static final class Window {
        private final Instant endsAt;
        private int count = 1;
        private Window(Instant endsAt) { this.endsAt = endsAt; }
    }
}
