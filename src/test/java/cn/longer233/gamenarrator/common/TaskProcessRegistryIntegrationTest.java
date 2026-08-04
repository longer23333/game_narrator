package cn.longer233.gamenarrator.common;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CancellationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskProcessRegistryIntegrationTest {

    @Test
    void cancellationTerminatesRegisteredChildProcessBeforeReportingSuccess() throws Exception {
        UUID taskId = UUID.randomUUID();
        String java = Path.of(System.getProperty("java.home"), "bin", executable("java")).toString();
        Process child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                Sleeper.class.getName()).start();
        try (var ignored = TaskProcessRegistry.open(taskId)) {
            TaskProcessRegistry.register(child);
            assertThat(child.isAlive()).isTrue();

            assertThat(TaskProcessRegistry.cancelAndAwait(taskId, Duration.ofSeconds(3))).isTrue();
            assertThat(child.isAlive()).isFalse();
            assertThatThrownBy(() -> TaskProcessRegistry.throwIfCancelled(taskId))
                    .isInstanceOf(CancellationException.class);
        } finally {
            if (child.isAlive()) ExternalProcessRunner.terminateTree(child);
        }
    }

    private String executable(String name) {
        return System.getProperty("os.name").toLowerCase().contains("win") ? name + ".exe" : name;
    }

    public static final class Sleeper {
        public static void main(String[] args) throws Exception {
            Thread.sleep(Duration.ofMinutes(5));
        }
    }
}
