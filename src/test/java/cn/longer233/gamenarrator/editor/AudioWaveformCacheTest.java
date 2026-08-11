package cn.longer233.gamenarrator.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioWaveformCacheTest {
    @TempDir Path temporary;

    @Test
    void cachesByFileIdentityAndPointCountAndInvalidatesAfterFileChange() throws Exception {
        Path audio = Files.writeString(temporary.resolve("audio.wav"), "first");
        AudioWaveformCache cache = new AudioWaveformCache(8);
        AtomicInteger loads = new AtomicInteger();

        assertThat(cache.get(audio, 320, () -> waveform(loads.incrementAndGet()))).containsEntry("generation", 1);
        assertThat(cache.get(audio, 320, () -> waveform(loads.incrementAndGet()))).containsEntry("generation", 1);
        assertThat(cache.get(audio, 640, () -> waveform(loads.incrementAndGet()))).containsEntry("generation", 2);

        Files.writeString(audio, "second-content");
        Files.setLastModifiedTime(audio, FileTime.fromMillis(System.currentTimeMillis() + 2_000));
        assertThat(cache.get(audio, 320, () -> waveform(loads.incrementAndGet()))).containsEntry("generation", 3);
    }

    @Test
    void concurrentRequestsShareOneDecodeAndFailuresAreNotCached() throws Exception {
        Path audio = Files.writeString(temporary.resolve("audio.wav"), "sound");
        AudioWaveformCache cache = new AudioWaveformCache(8);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> cache.get(audio, 320, () -> {
                loads.incrementAndGet(); entered.countDown(); release.await(); return waveform(1);
            }));
            entered.await();
            var second = executor.submit(() -> cache.get(audio, 320, () -> waveform(loads.incrementAndGet())));
            release.countDown();
            assertThat(first.get()).isEqualTo(second.get());
        }
        assertThat(loads).hasValue(1);

        assertThatThrownBy(() -> cache.get(audio, 640, () -> { throw new IllegalStateException("decode failed"); }))
                .isInstanceOf(IllegalStateException.class).hasMessage("decode failed");
        assertThat(cache.get(audio, 640, () -> waveform(loads.incrementAndGet()))).containsEntry("generation", 2);
    }

    @Test
    void evictsCompletedLeastRecentlyUsedEntries() throws Exception {
        AudioWaveformCache cache = new AudioWaveformCache(2);
        Path one = Files.writeString(temporary.resolve("one.wav"), "1");
        Path two = Files.writeString(temporary.resolve("two.wav"), "2");
        Path three = Files.writeString(temporary.resolve("three.wav"), "3");
        cache.get(one, 64, () -> waveform(1));
        cache.get(two, 64, () -> waveform(2));
        cache.get(three, 64, () -> waveform(3));
        assertThat(cache.size()).isEqualTo(2);
    }

    private Map<String, Object> waveform(int generation) {
        return Map.of("available", true, "points", List.of(.1, .5), "generation", generation);
    }
}
