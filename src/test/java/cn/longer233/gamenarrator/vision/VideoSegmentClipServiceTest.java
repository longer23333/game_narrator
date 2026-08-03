package cn.longer233.gamenarrator.vision;

import cn.longer233.gamenarrator.common.ExternalProcessRunner;
import cn.longer233.gamenarrator.task.domain.CommentaryStyle;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VideoSegmentClipServiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void createsBrowserPlayableMutedClipWithFfmpeg() throws Exception {
        Path source = temporaryDirectory.resolve("source.mp4");
        var generated = ExternalProcessRunner.run(List.of("ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-f", "lavfi", "-i", "testsrc2=size=320x180:rate=24:duration=2",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=2", "-shortest",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", source.toString()), Duration.ofSeconds(30));
        assertTrue(generated.exitCode() == 0 && Files.size(source) > 0, generated.output());

        UUID taskId = UUID.randomUUID();
        VideoTask task = new VideoTask("clip test", "ACTION", CommentaryStyle.ANIME_THEATER,
                30, "verify clipping", source.toString());
        var repository = mock(VideoTaskRepository.class);
        when(repository.findById(taskId)).thenReturn(Optional.of(task));
        var service = new VideoSegmentClipService(repository, "ffmpeg", temporaryDirectory.toString());

        Path clip = service.create(taskId, 0.4, 1, true);

        assertTrue(Files.isRegularFile(clip));
        assertTrue(Files.size(clip) > 1_000);
        var probe = ExternalProcessRunner.run(List.of("ffmpeg", "-hide_banner", "-i", clip.toString()), Duration.ofSeconds(15));
        assertTrue(probe.output().contains("Video: h264"), probe.output());
        assertTrue(!probe.output().contains("Audio:"), probe.output());
    }
}
