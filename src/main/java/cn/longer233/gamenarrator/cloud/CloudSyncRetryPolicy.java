package cn.longer233.gamenarrator.cloud;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class CloudSyncRetryPolicy {
    private final int maximumAttempts;
    private final long baseDelaySeconds;
    private final long maximumDelaySeconds;
    private final double jitterRatio;

    public CloudSyncRetryPolicy(
            @Value("${game-narrator.cloud-sync.retry.max-attempts:8}") int maximumAttempts,
            @Value("${game-narrator.cloud-sync.retry.base-delay-seconds:30}") long baseDelaySeconds,
            @Value("${game-narrator.cloud-sync.retry.max-delay-seconds:3600}") long maximumDelaySeconds,
            @Value("${game-narrator.cloud-sync.retry.jitter-ratio:0.2}") double jitterRatio) {
        this.maximumAttempts = Math.max(1, maximumAttempts);
        this.baseDelaySeconds = Math.max(1, baseDelaySeconds);
        this.maximumDelaySeconds = Math.max(this.baseDelaySeconds, maximumDelaySeconds);
        this.jitterRatio = Math.max(0, Math.min(.5, jitterRatio));
    }

    public boolean exhausted(int attemptCount) { return attemptCount >= maximumAttempts; }

    public Duration delay(int attemptCount, UUID itemId) {
        int exponent = Math.max(0, Math.min(30, attemptCount - 1));
        long exponential;
        try { exponential = Math.multiplyExact(baseDelaySeconds, 1L << exponent); }
        catch (ArithmeticException exception) { exponential = maximumDelaySeconds; }
        long capped = Math.min(maximumDelaySeconds, exponential);
        double unit = Math.floorMod(itemId.hashCode(), 10_001) / 10_000.0;
        double factor = 1 - jitterRatio + unit * jitterRatio * 2;
        return Duration.ofSeconds(Math.max(1, Math.round(capped * factor)));
    }

    public int maximumAttempts() { return maximumAttempts; }
}
