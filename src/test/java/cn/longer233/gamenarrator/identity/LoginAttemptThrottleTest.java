package cn.longer233.gamenarrator.identity;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptThrottleTest {
    @Test
    void locksOnlyMatchingUsernameAndClientAfterConfiguredFailures() {
        MutableClock clock = new MutableClock();
        LoginAttemptThrottle throttle = new LoginAttemptThrottle(3, Duration.ofMinutes(5), 100, clock);

        throttle.failed("creator", "client-a");
        throttle.failed("creator", "client-a");
        throttle.check("creator", "client-a");
        throttle.failed("creator", "client-a");

        assertThatThrownBy(() -> throttle.check("creator", "client-a"))
                .hasMessageContaining("过于频繁");
        throttle.check("creator", "client-b");
        throttle.check("another", "client-a");
    }

    @Test
    void successClearsFailuresAndExpiredLockCanRetry() {
        MutableClock clock = new MutableClock();
        LoginAttemptThrottle throttle = new LoginAttemptThrottle(2, Duration.ofMinutes(5), 100, clock);
        throttle.failed("creator", "client");
        throttle.succeeded("creator", "client");
        throttle.check("creator", "client");
        assertThat(throttle.size()).isZero();

        throttle.failed("creator", "client");
        throttle.failed("creator", "client");
        clock.advance(Duration.ofMinutes(6));
        throttle.check("creator", "client");
        assertThat(throttle.size()).isZero();
    }

    @Test
    void changingUserAgentDoesNotBypassSourceAddressLock() {
        MutableClock clock = new MutableClock();
        LoginAttemptThrottle throttle = new LoginAttemptThrottle(2, Duration.ofMinutes(5), 100, clock);
        throttle.failed("creator", "203.0.113.8 | agent-a");
        throttle.failed("creator", "203.0.113.8 | agent-b");

        assertThatThrownBy(() -> throttle.check("creator", "203.0.113.8 | agent-c"))
                .hasMessageContaining("过于频繁");
        throttle.check("creator", "203.0.113.9 | agent-a");
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-08-23T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
