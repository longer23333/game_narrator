package cn.longer233.gamenarrator.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@Component
public class AssetSearchResilience {
    private final int maxAttempts;
    private final long initialBackoffMillis;
    private final long freshMillis;
    private final long staleMillis;
    private final int maximumEntries;
    private final Clock clock;
    private final Sleeper sleeper;
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);

    @Autowired
    public AssetSearchResilience(
            @Value("${game-narrator.asset-library.search-resilience.max-attempts:3}") int maxAttempts,
            @Value("${game-narrator.asset-library.search-resilience.initial-backoff-millis:200}") long initialBackoffMillis,
            @Value("${game-narrator.asset-library.search-resilience.fresh-ttl-minutes:10}") long freshTtlMinutes,
            @Value("${game-narrator.asset-library.search-resilience.stale-if-error-hours:6}") long staleIfErrorHours,
            @Value("${game-narrator.asset-library.search-resilience.maximum-entries:256}") int maximumEntries) {
        this(maxAttempts, initialBackoffMillis, Duration.ofMinutes(freshTtlMinutes),
                Duration.ofHours(staleIfErrorHours), maximumEntries, Clock.systemUTC(), Thread::sleep);
    }

    AssetSearchResilience(int maxAttempts, long initialBackoffMillis, Duration freshTtl,
                          Duration staleTtl, int maximumEntries, Clock clock, Sleeper sleeper) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoffMillis = Math.max(0, initialBackoffMillis);
        this.freshMillis = Math.max(0, freshTtl.toMillis());
        this.staleMillis = Math.max(this.freshMillis, staleTtl.toMillis());
        this.maximumEntries = Math.max(1, maximumEntries);
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public JsonNode execute(String provider, String exactQueryKey, Callable<JsonNode> request) {
        String key = provider + "\n" + exactQueryKey;
        long now = clock.millis();
        CacheEntry cached = get(key);
        if (cached != null && now - cached.createdAt <= freshMillis) {
            return decorate(cached.value.deepCopy(), "FRESH_CACHE", 0, null);
        }
        RuntimeException lastFailure = null;
        int attempts = 0;
        while (attempts < maxAttempts) {
            attempts++;
            try {
                JsonNode value = request.call();
                if (value == null) throw new IllegalStateException(provider + " returned an empty response");
                put(key, new CacheEntry(value.deepCopy(), clock.millis()));
                return decorate(value, "NETWORK", attempts, null);
            } catch (Exception failure) {
                lastFailure = failure instanceof RuntimeException runtime
                        ? runtime : new IllegalStateException(failure);
                if (attempts >= maxAttempts || !retryable(lastFailure)) break;
                sleep(backoff(attempts, key));
            }
        }
        now = clock.millis();
        if (cached != null && lastFailure != null && retryable(lastFailure)
                && now - cached.createdAt <= staleMillis) {
            String warning = provider + " 暂时不可用，已显示相同查询的近期缓存结果";
            return decorate(cached.value.deepCopy(), "STALE_IF_ERROR", attempts, warning);
        }
        throw lastFailure == null ? new IllegalStateException(provider + " search failed") : lastFailure;
    }

    private boolean retryable(RuntimeException failure) {
        if (failure instanceof ResourceAccessException) return true;
        if (failure instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        return false;
    }

    private long backoff(int failedAttempt, String key) {
        long exponential = initialBackoffMillis * (1L << Math.min(20, failedAttempt - 1));
        long jitter = exponential == 0 ? 0 : Math.floorMod(key.hashCode() * 31L + failedAttempt, Math.max(1, exponential / 4));
        return exponential + jitter;
    }

    private void sleep(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("素材搜索重试被中断", interrupted);
        }
    }

    private JsonNode decorate(JsonNode value, String source, int attempts, String warning) {
        if (value instanceof ObjectNode object) {
            ObjectNode metadata = object.putObject("_resilience");
            metadata.put("source", source);
            metadata.put("attempts", attempts);
            if (warning != null) metadata.put("warning", warning);
        }
        return value;
    }

    private synchronized CacheEntry get(String key) { return cache.get(key); }

    private synchronized void put(String key, CacheEntry entry) {
        cache.put(key, entry);
        while (cache.size() > maximumEntries) cache.remove(cache.keySet().iterator().next());
    }

    @FunctionalInterface
    interface Sleeper { void sleep(long millis) throws InterruptedException; }
    private record CacheEntry(JsonNode value, long createdAt) { }
}
