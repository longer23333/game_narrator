package cn.longer233.gamenarrator.common;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalProcessRunnerTest {
    @Test
    void allowsIdempotentConfigurationWhileAProcessIsActive() throws Exception {
        ExternalProcessRunner.configureLimits(1, 1, 2);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = Path.of("target", "test-classes") + File.pathSeparator + Path.of("target", "classes");
        CountDownLatch started = new CountDownLatch(1);
        CompletableFuture<Void> running = CompletableFuture.runAsync(() -> {
            started.countDown();
            run(List.of(java, "-cp", classpath, ProcessSleeper.class.getName(), "400"));
        });
        started.await();
        while (ExternalProcessRunner.activeCounts().get("other") == 0) Thread.onSpinWait();
        ExternalProcessRunner.configureLimits(1, 1, 2);
        running.join();
    }

    @Test
    void serializesProcessesWithinTheConfiguredTypeLimit() {
        ExternalProcessRunner.configureLimits(1, 1, 1);
        try {
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            String classpath = Path.of("target", "test-classes") + File.pathSeparator
                    + Path.of("target", "classes");
            List<String> command = List.of(java, "-cp", classpath,
                    ProcessSleeper.class.getName(), "400");
            long started = System.nanoTime();
            CompletableFuture.allOf(
                    CompletableFuture.runAsync(() -> run(command)),
                    CompletableFuture.runAsync(() -> run(command))
            ).join();
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThan(Duration.ofMillis(650));
            assertThat(ExternalProcessRunner.activeCounts()).containsEntry("other", 0);
        } finally {
            ExternalProcessRunner.configureLimits(1, 1, 2);
        }
    }

    private void run(List<String> command) {
        try {
            assertThat(ExternalProcessRunner.run(command, Duration.ofSeconds(5)).exitCode()).isZero();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
