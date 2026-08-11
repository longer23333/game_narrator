package cn.longer233.gamenarrator.editor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Component
public class AudioWaveformCache {
    private final int maximumEntries;
    private final Map<Key, CompletableFuture<Map<String, Object>>> entries =
            new LinkedHashMap<>(16, 0.75f, true);

    public AudioWaveformCache(
            @Value("${game-narrator.editor.waveform-cache.maximum-entries:128}") int maximumEntries) {
        this.maximumEntries = Math.max(1, maximumEntries);
    }

    public Map<String, Object> get(Path audio, int points, Callable<Map<String, Object>> loader) {
        Key key = key(audio, points);
        CompletableFuture<Map<String, Object>> future;
        boolean load = false;
        synchronized (entries) {
            future = entries.get(key);
            if (future == null) {
                future = new CompletableFuture<>();
                entries.put(key, future);
                trim();
                load = true;
            }
        }
        if (load) {
            try {
                future.complete(Map.copyOf(loader.call()));
            } catch (Exception failure) {
                future.completeExceptionally(failure);
                synchronized (entries) { entries.remove(key, future); }
            }
        }
        try {
            return future.join();
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(cause == null ? failure : cause);
        }
    }

    int size() {
        synchronized (entries) { return entries.size(); }
    }

    private Key key(Path audio, int points) {
        try {
            Path normalized = audio.toAbsolutePath().normalize();
            return new Key(normalized, Files.size(normalized), Files.getLastModifiedTime(normalized).toMillis(), points);
        } catch (IOException failure) {
            throw new IllegalStateException("无法读取音频文件信息：" + failure.getMessage(), failure);
        }
    }

    private void trim() {
        while (entries.size() > maximumEntries) {
            var iterator = entries.entrySet().iterator();
            while (iterator.hasNext()) {
                var candidate = iterator.next();
                if (candidate.getValue().isDone()) {
                    iterator.remove();
                    break;
                }
            }
            if (entries.size() > maximumEntries
                    && entries.values().stream().noneMatch(CompletableFuture::isDone)) return;
        }
    }

    private record Key(Path path, long size, long modifiedAt, int points) { }
}
