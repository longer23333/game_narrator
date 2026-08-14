package cn.longer233.gamenarrator.performance;

import cn.longer233.gamenarrator.common.ExternalProcessRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_RELEASE_SOAK", matches = "true")
class ReleaseSoakPerformanceIT {
    @TempDir Path temporary;

    @Test
    void repeatedlyTranscodesRealAudioVideoForConfiguredDurationWithoutHeapDrift() throws Exception {
        int minutes = Integer.parseInt(System.getenv().getOrDefault("RELEASE_SOAK_MINUTES", "60"));
        assertThat(minutes).isBetween(1, 1440);
        Path source = temporary.resolve("soak-source.mp4");
        var generated = ExternalProcessRunner.run(List.of("ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=30:duration=30",
                "-f", "lavfi", "-i", "sine=frequency=880:duration=30", "-shortest",
                "-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p", "-c:a", "aac",
                source.toString()), Duration.ofMinutes(3));
        assertThat(generated.exitCode()).isZero();
        assertThat(Files.size(source)).isPositive();

        long deadline = System.nanoTime() + Duration.ofMinutes(minutes).toNanos();
        long startingHeap = usedHeap();
        long maximumHeap = startingHeap;
        int iterations = 0;
        List<Long> elapsedMillis = new ArrayList<>();
        while (System.nanoTime() < deadline) {
            Path output = temporary.resolve("soak-" + iterations + ".mp4");
            long started = System.nanoTime();
            var result = ExternalProcessRunner.run(List.of("ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                    "-i", source.toString(), "-vf", "scale=854:480", "-c:v", "libx264", "-preset", "veryfast",
                    "-c:a", "aac", output.toString()), Duration.ofMinutes(3));
            elapsedMillis.add(Duration.ofNanos(System.nanoTime() - started).toMillis());
            assertThat(result.exitCode()).isZero();
            assertThat(Files.size(output)).isPositive();
            Files.delete(output);
            iterations++;
            if (iterations % 5 == 0) {
                System.gc();
                Thread.sleep(100);
                maximumHeap = Math.max(maximumHeap, usedHeap());
            }
        }
        System.gc();
        Thread.sleep(250);
        long finalHeap = usedHeap();
        long maximumIterationMillis = elapsedMillis.stream().mapToLong(Long::longValue).max().orElseThrow();
        assertThat(iterations).isPositive();
        assertThat(maximumIterationMillis).isLessThan(Duration.ofMinutes(3).toMillis());
        assertThat(finalHeap - startingHeap).isLessThan(128L * 1024 * 1024);
        System.out.printf("GN_SOAK_METRICS iterations=%d maxIterationMs=%d startingHeap=%d maxHeap=%d finalHeap=%d%n",
                iterations, maximumIterationMillis, startingHeap, maximumHeap, finalHeap);
    }

    private static long usedHeap() {
        var memory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return memory.getUsed();
    }
}
