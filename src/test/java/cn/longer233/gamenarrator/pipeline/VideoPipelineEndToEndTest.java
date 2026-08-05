package cn.longer233.gamenarrator.pipeline;

import cn.longer233.gamenarrator.common.ExternalProcessRunner;
import cn.longer233.gamenarrator.editor.EditorCommandRequest;
import cn.longer233.gamenarrator.editor.EditorTimelineService;
import cn.longer233.gamenarrator.task.application.CreateVideoTaskCommand;
import cn.longer233.gamenarrator.task.application.VideoTaskService;
import cn.longer233.gamenarrator.task.domain.CommentaryStyle;
import cn.longer233.gamenarrator.task.domain.EditingScope;
import cn.longer233.gamenarrator.task.domain.TaskStatus;
import cn.longer233.gamenarrator.task.domain.StageStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pipeline-e2e",
        "game-narrator.storage-root=./target/pipeline-e2e-storage",
        "game-narrator.ffmpeg-command=ffmpeg",
        "game-narrator.render.video-encoder=libx264",
        "game-narrator.media-import.yt-dlp=./mvnw.cmd",
        "game-narrator.scene-analysis-fps=1",
        "game-narrator.maximum-scene-frames=10"
})
class VideoPipelineEndToEndTest {
    @Autowired VideoTaskService tasks;
    @Autowired EditorTimelineService editor;
    @TempDir Path temporary;

    @Test
    void processesFiveSecondVideoThroughAllNineStagesUsingRealFfmpeg() throws Exception {
        Path source = temporary.resolve("five-seconds.mp4");
        var generated = ExternalProcessRunner.run(List.of("ffmpeg",
                "-y", "-hide_banner", "-loglevel", "error", "-f", "lavfi",
                "-i", "color=c=blue:s=320x180:d=5", "-f", "lavfi", "-i", "sine=frequency=880:duration=5",
                "-shortest", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac",
                source.toString()), Duration.ofSeconds(30));
        assertThat(generated.exitCode()).isZero();

        var command = new CreateVideoTaskCommand("pipeline e2e", "ACTION",
                CommentaryStyle.ANIME_THEATER, 15, EditingScope.FULL_VIDEO,
                "exercise every stage", "", false, true, false, false, false, false);
        var created = tasks.create(command, new MockMultipartFile("video", "five-seconds.mp4",
                "video/mp4", Files.readAllBytes(source)));

        long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
        var current = tasks.find(created.id());
        while (current.status() != TaskStatus.COMPLETED && current.status() != TaskStatus.FAILED
                && System.nanoTime() < deadline) {
            Thread.sleep(250);
            current = tasks.find(created.id());
        }

        assertThat(current.failureReason()).isNull();
        assertThat(current.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(current.stages()).hasSize(9).allMatch(stage -> stage.status() == StageStatus.COMPLETED);
        assertThat(Path.of(current.renderedVideoPath())).isRegularFile();
        assertThat(Path.of(current.sceneManifestPath()).resolveSibling("audio-analysis.json")).isRegularFile();
    }

    @Test
    void preparesCompleteEditableTimelineWithoutAnyAiOrAutomaticRender() throws Exception {
        Path source = temporary.resolve("manual-five-seconds.mp4");
        var generated = ExternalProcessRunner.run(List.of("ffmpeg",
                "-y", "-hide_banner", "-loglevel", "error", "-f", "lavfi",
                "-i", "color=c=green:s=320x180:d=5", "-f", "lavfi", "-i", "sine=frequency=440:duration=5",
                "-shortest", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac",
                source.toString()), Duration.ofSeconds(30));
        assertThat(generated.exitCode()).isZero();

        var command = new CreateVideoTaskCommand("manual editor e2e", "ACTION",
                CommentaryStyle.ANIME_THEATER, 15, EditingScope.FULL_VIDEO,
                "manual editing without AI", "", false, false, false, false, false, false);
        var created = tasks.create(command, new MockMultipartFile("video", "manual-five-seconds.mp4",
                "video/mp4", Files.readAllBytes(source)));

        long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
        var current = tasks.find(created.id());
        while (current.timelinePath() == null && current.status() != TaskStatus.FAILED
                && System.nanoTime() < deadline) {
            Thread.sleep(250);
            current = tasks.find(created.id());
        }

        assertThat(current.failureReason()).isNull();
        assertThat(current.status()).isEqualTo(TaskStatus.READY);
        assertThat(current.generatedScriptPath()).isNotBlank();
        assertThat(current.voiceManifestPath()).isNotBlank();
        assertThat(Path.of(current.timelinePath())).isRegularFile();
        assertThat(current.renderedVideoPath()).isNull();
        assertThat(current.stages().stream().filter(stage -> stage.status() == StageStatus.COMPLETED)).hasSize(8);
        assertThat(current.stages().getLast().status()).isEqualTo(StageStatus.PENDING);

        var timeline = editor.timeline(created.id());
        var first = timeline.path("clips").get(0);
        double splitAt = first.path("timelineStartSeconds").asDouble()
                + first.path("durationSeconds").asDouble() / 2;
        var split = editor.command(created.id(), new EditorCommandRequest("SPLIT", java.util.Map.of(
                "clipId", first.path("id").asText(), "atSeconds", splitAt)));
        assertThat(split.path("clips")).hasSize(2);
        assertThat(tasks.find(created.id()).timelinePath()).isNull();

        var merged = editor.command(created.id(), new EditorCommandRequest("MERGE", java.util.Map.of(
                "clipId", split.path("clips").get(0).path("id").asText())));
        assertThat(merged.path("clips")).hasSize(1);

        var restored = editor.command(created.id(), new EditorCommandRequest("UNDO", java.util.Map.of()));
        assertThat(restored.path("clips")).hasSize(2);
        assertThat(restored.path("history").path("canRedo").asBoolean()).isTrue();

        var deleted = editor.command(created.id(), new EditorCommandRequest("DELETE", java.util.Map.of(
                "clipId", restored.path("clips").get(1).path("id").asText())));
        assertThat(deleted.path("clips")).hasSize(1);
    }
}
