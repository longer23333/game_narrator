package cn.longer233.gamenarrator.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticLogServiceTest {
    @TempDir Path tempDir;

    @Test
    void tailsLogsAndRedactsCredentials() throws Exception {
        Path log = tempDir.resolve("game-narrator.log");
        Files.writeString(log, "normal line\nAuthorization: Bearer abc.def\napiKey=private-value\nCookie: SESSDATA=secret\n"
                + "{\"client_secret\":\"json-secret with spaces\",\"access_token\":\"access-value\"}\n"
                + "refresh_token=refresh-value session-token=session-value\n");
        var service = new DiagnosticLogService(log.toString());

        String recent = service.recent(100);

        assertThat(recent).contains("normal line", "Authorization=***", "apiKey=***", "Cookie=***")
                .doesNotContain("abc.def", "private-value", "SESSDATA=secret", "json-secret with spaces",
                        "access-value", "refresh-value", "session-value");
        assertThat(service.export()).isNotEmpty();
    }

    @Test
    void filtersTaskLogsAndKeepsFailureContext() throws Exception {
        UUID target = UUID.randomUUID();
        UUID another = UUID.randomUUID();
        Path log = tempDir.resolve("game-narrator.log");
        Files.writeString(log, "unrelated\n"
                + "ENGINE_FAILED taskId=" + target + " stage=RENDERING message=ffmpeg failed\n"
                + "java.lang.IllegalStateException: complete ffmpeg output\n"
                + "\tat renderer.call(Renderer.java:42)\n"
                + "Authorization: Bearer secret-token\n"
                + "ENGINE_COMPLETED taskId=" + another + "\n");
        var service = new DiagnosticLogService(log.toString());

        String recent = service.recentForTask(target, 100);

        assertThat(recent).contains(target.toString(), "complete ffmpeg output", "renderer.call", "Authorization=***")
                .doesNotContain(another.toString(), "secret-token");
    }

    @Test
    void filtersLogsForAllTasksOwnedByOneAccount() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        Path log = tempDir.resolve("game-narrator.log");
        Files.writeString(log, "ENGINE_FAILED taskId=" + foreign + " message=foreign-secret\n"
                + "foreign stack detail\n"
                + "ENGINE_START taskId=" + first + "\n"
                + "ENGINE_FAILED taskId=" + second + " message=owned failure\n"
                + "owned stack detail\n");
        var service = new DiagnosticLogService(log.toString());

        String recent = service.recentForTasks(java.util.List.of(first, second), 100);

        assertThat(recent).contains(first.toString(), second.toString(), "owned stack detail")
                .doesNotContain(foreign.toString(), "foreign-secret", "foreign stack detail");
    }
}
