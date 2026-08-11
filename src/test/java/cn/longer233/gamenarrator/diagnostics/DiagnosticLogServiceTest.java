package cn.longer233.gamenarrator.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

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
}
