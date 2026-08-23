package cn.longer233.gamenarrator.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded per-account/per-client failed-login limiter. No passwords or session tokens are retained. */
@Component
final class LoginAttemptThrottle {
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final int maximumFailures;
    private final int maximumEntries;
    private final Duration lockDuration;
    private final Clock clock;

    @Autowired
    LoginAttemptThrottle(
            @Value("${game-narrator.auth.login-throttle.maximum-failures:5}") int maximumFailures,
            @Value("${game-narrator.auth.login-throttle.lock-minutes:15}") int lockMinutes,
            @Value("${game-narrator.auth.login-throttle.maximum-entries:10000}") int maximumEntries) {
        this(maximumFailures, Duration.ofMinutes(lockMinutes), maximumEntries, Clock.systemUTC());
    }

    LoginAttemptThrottle(int maximumFailures, Duration lockDuration, int maximumEntries, Clock clock) {
        this.maximumFailures = Math.max(2, Math.min(20, maximumFailures));
        this.lockDuration = lockDuration.isNegative() || lockDuration.isZero() ? Duration.ofMinutes(15) : lockDuration;
        this.maximumEntries = Math.max(100, Math.min(100_000, maximumEntries));
        this.clock = clock;
    }

    void check(String username, String client) {
        Attempt attempt = attempts.get(key(username, client));
        Instant now = clock.instant();
        if (attempt == null) return;
        if (!attempt.expiresAt().isAfter(now)) {
            attempts.remove(key(username, client), attempt);
            return;
        }
        if (attempt.failures() >= maximumFailures) {
            throw new IllegalArgumentException("登录尝试过于频繁，请稍后重试");
        }
    }

    void failed(String username, String client) {
        Instant now = clock.instant();
        String key = key(username, client);
        attempts.compute(key, (ignored, current) -> {
            int failures = current == null || !current.expiresAt().isAfter(now)
                    ? 1 : Math.min(maximumFailures, current.failures() + 1);
            return new Attempt(failures, now.plus(lockDuration), now);
        });
        trimIfNecessary();
    }

    void succeeded(String username, String client) {
        attempts.remove(key(username, client));
    }

    int size() { return attempts.size(); }

    private void trimIfNecessary() {
        int excess = attempts.size() - maximumEntries;
        if (excess <= 0) return;
        attempts.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getValue().updatedAt()))
                .limit(excess)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(attempts::remove);
    }

    private String key(String username, String client) {
        String source=client == null ? "unknown" : client.strip().toLowerCase(java.util.Locale.ROOT);
        int agentSeparator=source.indexOf(" | ");
        if (agentSeparator>=0) source=source.substring(0,agentSeparator);
        return username + "\n" + source;
    }

    private record Attempt(int failures, Instant expiresAt, Instant updatedAt) { }
}
