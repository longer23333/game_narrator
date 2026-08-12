package cn.longer233.gamenarrator.performance;

import cn.longer233.gamenarrator.common.ExternalProcessRunner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit heavy gate: a five-minute 1080p transcode must stay below 2x realtime without retained heap growth. */
@Tag("performance")
@EnabledIfEnvironmentVariable(named = "RUN_LONG_VIDEO_PERFORMANCE", matches = "true")
class LongVideoPerformanceIT {
    @TempDir Path temporary;

    @Test
    void transcodesFiveMinute1080pVideoWithinTwiceRealtimeAndBoundedHeap() throws Exception {
        Path source = temporary.resolve("five-minute-1080p.mp4");
        Path output = temporary.resolve("transcoded.mp4");
        var generated = ExternalProcessRunner.run(List.of("ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-f", "lavfi", "-i", "testsrc2=size=1920x1080:rate=30:duration=300",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=300", "-shortest",
                "-c:v", "libx264", "-preset", "ultrafast", "-c:a", "aac", source.toString()), Duration.ofMinutes(10));
        assertThat(generated.exitCode()).isZero();
        System.gc();
        long before = usedHeap();
        long started = System.nanoTime();
        var transcoded = ExternalProcessRunner.run(List.of("ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-i", source.toString(), "-c:v", "libx264", "-preset", "veryfast", "-c:a", "aac", output.toString()),
                Duration.ofMinutes(10));
        long elapsedSeconds = Duration.ofNanos(System.nanoTime() - started).toSeconds();
        System.gc();
        long maximumHeapGrowthBytes = 64L * 1024 * 1024;
        assertThat(transcoded.exitCode()).isZero();
        assertThat(elapsedSeconds).as("five-minute 1080p processing seconds").isLessThan(600L);
        assertThat(usedHeap() - before).as("retained Java heap after long transcode").isLessThan(maximumHeapGrowthBytes);
        assertThat(Files.size(output)).isPositive();
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
