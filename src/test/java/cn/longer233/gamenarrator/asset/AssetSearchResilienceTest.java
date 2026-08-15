package cn.longer233.gamenarrator.asset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetSearchResilienceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void retriesTimeoutWithExponentialBackoffThenCachesExactQuery() {
        MutableClock clock = new MutableClock();
        List<Long> sleeps = new ArrayList<>();
        AssetSearchResilience resilience = new AssetSearchResilience(3, 100, Duration.ofMinutes(10),
                Duration.ofHours(6), 10, clock, sleeps::add);
        AtomicInteger calls = new AtomicInteger();

        var first = resilience.execute("WIKIMEDIA", "/images?q=cat", () -> {
            if (calls.incrementAndGet() < 3) throw timeout();
            var response = mapper.createObjectNode();
            response.putArray("results").add("cat");
            return response;
        });
        var second = resilience.execute("WIKIMEDIA", "/images?q=cat", () -> {
            throw new AssertionError("fresh cache must avoid the network");
        });

        assertThat(calls).hasValue(3);
        assertThat(sleeps).hasSize(2);
        assertThat(sleeps.get(1)).isGreaterThan(sleeps.get(0));
        assertThat(first.path("_resilience").path("source").asText()).isEqualTo("NETWORK");
        assertThat(second.path("_resilience").path("source").asText()).isEqualTo("FRESH_CACHE");
    }

    @Test
    void staleFallbackIsIsolatedByExactQueryAndExpires() {
        MutableClock clock = new MutableClock();
        AssetSearchResilience resilience = new AssetSearchResilience(1, 0, Duration.ofMinutes(1),
                Duration.ofHours(1), 10, clock, ignored -> { });
        resilience.execute("WIKIMEDIA", "q=cat&page=1", () -> mapper.createObjectNode().put("value", "cat"));
        clock.advance(Duration.ofMinutes(2));

        var stale = resilience.execute("WIKIMEDIA", "q=cat&page=1", () -> { throw timeout(); });
        assertThat(stale.path("value").asText()).isEqualTo("cat");
        assertThat(stale.path("_resilience").path("source").asText()).isEqualTo("STALE_IF_ERROR");
        assertThatThrownBy(() -> resilience.execute("WIKIMEDIA", "q=dog&page=1", () -> { throw timeout(); }))
                .isInstanceOf(ResourceAccessException.class);

        clock.advance(Duration.ofHours(2));
        assertThatThrownBy(() -> resilience.execute("WIKIMEDIA", "q=cat&page=1", () -> { throw timeout(); }))
                .isInstanceOf(ResourceAccessException.class);
    }

    private ResourceAccessException timeout() {
        return new ResourceAccessException("timeout", new SocketTimeoutException("read timed out"));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-08-11T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
