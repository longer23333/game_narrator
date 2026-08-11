package cn.longer233.gamenarrator.importer;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThumbnailMemoryCacheTest {
    @Test
    void concurrentRequestsShareOneDownloadAndFailuresAreRetried() throws Exception {
        ThumbnailMemoryCache cache = new ThumbnailMemoryCache(8, Duration.ofMinutes(10));
        AtomicInteger downloads = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> cache.get("same", () -> {
                downloads.incrementAndGet(); entered.countDown(); release.await(); return content(1);
            }));
            entered.await();
            var second = executor.submit(() -> cache.get("same", () -> content(downloads.incrementAndGet())));
            release.countDown();
            assertThat(first.get().bytes()).isEqualTo(second.get().bytes());
        }
        assertThat(downloads).hasValue(1);

        assertThatThrownBy(() -> cache.get("failed", () -> { throw new IllegalStateException("network"); }))
                .hasMessage("network");
        assertThat(cache.get("failed", () -> content(downloads.incrementAndGet())).bytes()).containsExactly(2);
    }

    @Test
    void expiresEntriesAndEvictsLeastRecentlyUsedCompletedEntry() {
        MutableClock clock = new MutableClock();
        ThumbnailMemoryCache cache = new ThumbnailMemoryCache(4, Duration.ofMinutes(1), clock);
        AtomicInteger downloads = new AtomicInteger();
        cache.get("one", () -> content(downloads.incrementAndGet()));
        cache.get("one", () -> content(downloads.incrementAndGet()));
        assertThat(downloads).hasValue(1);
        clock.advance(Duration.ofMinutes(2));
        cache.get("one", () -> content(downloads.incrementAndGet()));
        assertThat(downloads).hasValue(2);

        for (int index = 0; index < 5; index++) {
            int value = index;
            cache.get("item-" + index, () -> content(value));
        }
        assertThat(cache.size()).isEqualTo(4);
    }

    private RemoteThumbnailService.ThumbnailContent content(int value) {
        return new RemoteThumbnailService.ThumbnailContent("image/jpeg", new byte[]{(byte) value});
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-08-11T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
