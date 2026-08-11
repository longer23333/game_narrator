package cn.longer233.gamenarrator.importer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

final class ThumbnailMemoryCache {
    private final int capacity;
    private final Duration ttl;
    private final Clock clock;
    private final Map<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

    ThumbnailMemoryCache(int capacity, Duration ttl) {
        this(capacity, ttl, Clock.systemUTC());
    }

    ThumbnailMemoryCache(int capacity, Duration ttl, Clock clock) {
        this.capacity = Math.max(4, Math.min(512, capacity));
        this.ttl = ttl.compareTo(Duration.ofMinutes(1)) < 0 ? Duration.ofMinutes(1) : ttl;
        this.clock = clock;
    }

    RemoteThumbnailService.ThumbnailContent get(String key,
            Callable<RemoteThumbnailService.ThumbnailContent> loader) {
        Entry entry;
        boolean load = false;
        synchronized (entries) {
            entry = entries.get(key);
            if (entry != null && entry.future.isDone() && !entry.expiresAt.isAfter(clock.instant())) {
                entries.remove(key);
                entry = null;
            }
            if (entry == null) {
                entry = new Entry(new CompletableFuture<>(), clock.instant().plus(ttl));
                entries.put(key, entry);
                trimCompletedEntries();
                load = true;
            }
        }
        if (load) {
            try {
                entry.future.complete(loader.call());
            } catch (Exception failure) {
                entry.future.completeExceptionally(failure);
                synchronized (entries) { entries.remove(key, entry); }
            }
        }
        try {
            return entry.future.join();
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(cause == null ? failure : cause);
        }
    }

    int size() {
        synchronized (entries) { return entries.size(); }
    }

    private void trimCompletedEntries() {
        while (entries.size() > capacity) {
            var iterator = entries.entrySet().iterator();
            boolean removed = false;
            while (iterator.hasNext()) {
                if (iterator.next().getValue().future.isDone()) {
                    iterator.remove();
                    removed = true;
                    break;
                }
            }
            if (!removed) return;
        }
    }

    private record Entry(CompletableFuture<RemoteThumbnailService.ThumbnailContent> future,
                         Instant expiresAt) { }
}
