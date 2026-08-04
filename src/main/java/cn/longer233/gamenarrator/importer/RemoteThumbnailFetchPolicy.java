package cn.longer233.gamenarrator.importer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

final class RemoteThumbnailFetchPolicy {
    private final Semaphore permits;
    private final long acquireTimeoutMs;
    private final Duration failureTtl;
    private final Clock clock;
    private final Map<String, Failure> failures;

    RemoteThumbnailFetchPolicy(int maximumConcurrent, long acquireTimeoutMs,
                               int maximumFailures, Duration failureTtl) {
        this(maximumConcurrent, acquireTimeoutMs, maximumFailures, failureTtl, Clock.systemUTC());
    }

    RemoteThumbnailFetchPolicy(int maximumConcurrent, long acquireTimeoutMs,
                               int maximumFailures, Duration failureTtl, Clock clock) {
        int concurrency = Math.max(1, Math.min(16, maximumConcurrent));
        this.permits = new Semaphore(concurrency, true);
        this.acquireTimeoutMs = Math.max(50, Math.min(5_000, acquireTimeoutMs));
        this.failureTtl = failureTtl.compareTo(Duration.ofSeconds(5)) < 0
                ? Duration.ofSeconds(5) : failureTtl;
        this.clock = clock;
        int capacity = Math.max(8, Math.min(512, maximumFailures));
        this.failures = java.util.Collections.synchronizedMap(new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Failure> eldest) {
                return size() > capacity;
            }
        });
    }

    Permit acquire(String key) {
        rejectCachedFailure(key);
        try {
            if (!permits.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("封面请求较多，已使用本地占位图");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("封面请求已中断", exception);
        }
        try {
            rejectCachedFailure(key);
            return new Permit(permits);
        } catch (RuntimeException exception) {
            permits.release();
            throw exception;
        }
    }

    void failed(String key, RuntimeException exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        failures.put(key, new Failure(message, clock.instant().plus(failureTtl)));
    }

    void succeeded(String key) {
        failures.remove(key);
    }

    private void rejectCachedFailure(String key) {
        Failure failure = failures.get(key);
        if (failure == null) return;
        if (!failure.expiresAt().isAfter(clock.instant())) {
            failures.remove(key);
            return;
        }
        throw new IllegalStateException("封面源暂时不可用（失败缓存）：" + failure.message());
    }

    static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private boolean closed;

        private Permit(Semaphore semaphore) { this.semaphore = semaphore; }

        @Override public void close() {
            if (!closed) {
                closed = true;
                semaphore.release();
            }
        }
    }

    private record Failure(String message, Instant expiresAt) { }
}
