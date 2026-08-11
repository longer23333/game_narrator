package cn.longer233.gamenarrator.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import cn.longer233.gamenarrator.task.domain.ProcessingStageType;

class StorageCleanupServiceTest {
    @TempDir Path temporary;

    @Test
    void removesOnlyExpiredTemporaryAndImportFiles() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        Path oldTemporary = temporary.resolve("tasks/a/.artifact.tmp");
        Path recentTemporary = temporary.resolve("tasks/a/.recent.tmp");
        Path oldImport = temporary.resolve("import-downloads/job/video.mp4");
        Files.createDirectories(oldTemporary.getParent());
        Files.createDirectories(oldImport.getParent());
        Files.writeString(oldTemporary, "old");
        Files.writeString(recentTemporary, "recent");
        Files.writeString(oldImport, "old");
        FileTime old = FileTime.from(Instant.now().minus(2, ChronoUnit.DAYS));
        Files.setLastModifiedTime(oldTemporary, old);
        Files.setLastModifiedTime(oldImport, old);

        new StorageCleanupService(temporary.toString(), 24, jdbc).cleanup();

        assertThat(oldTemporary).doesNotExist();
        assertThat(oldImport).doesNotExist();
        assertThat(recentTemporary).exists();
    }

    @Test
    void taskCleanupRemovesOwnedSegmentAndKnownImportArtifacts() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        UUID taskId = UUID.randomUUID();
        Path clip = temporary.resolve("segment-clips").resolve(taskId + "-0_000-5_000.mp4");
        Path imported = temporary.resolve("import-downloads").resolve(taskId + "-source.mp4");
        Files.createDirectories(clip.getParent());
        Files.createDirectories(imported.getParent());
        Files.writeString(clip, "clip");
        Files.writeString(imported, "source");

        new StorageCleanupService(temporary.toString(), 24, jdbc)
                .cleanupTask(taskId, List.of(imported.toString()));

        assertThat(clip).doesNotExist();
        assertThat(imported).doesNotExist();
    }

    @Test
    void retryCleanupRemovesOnlyTheFailedStageAndTemporaryArtifacts() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID taskId = UUID.randomUUID();
        Path task = temporary.resolve("tasks").resolve(taskId.toString());
        Path completedScript = task.resolve("generated-script.json");
        Path partialVoice = task.resolve("voice/voice-00.wav");
        Path partialManifest = task.resolve("voice-manifest.json");
        Path temporaryPart = task.resolve("voice/chunk.part");
        Files.createDirectories(partialVoice.getParent());
        Files.writeString(completedScript, "keep");
        Files.writeString(partialVoice, "partial");
        Files.writeString(partialManifest, "partial");
        Files.writeString(temporaryPart, "partial");

        new StorageCleanupService(temporary.toString(), 24, jdbc)
                .cleanupRetryArtifacts(taskId, ProcessingStageType.VOICE_GENERATION);

        assertThat(completedScript).exists();
        assertThat(partialVoice).doesNotExist();
        assertThat(partialManifest).doesNotExist();
        assertThat(temporaryPart).doesNotExist();
    }
}
