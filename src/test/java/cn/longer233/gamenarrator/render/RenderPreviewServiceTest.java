package cn.longer233.gamenarrator.render;

import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import cn.longer233.gamenarrator.pipeline.TaskArtifactLocator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RenderPreviewServiceTest {
    @TempDir Path storageRoot;

    @Test
    void readsIncrementalManifestAndServesOwnedFrame() throws Exception {
        UUID taskId = UUID.randomUUID();
        Path taskDirectory = Files.createDirectories(storageRoot.resolve("task"));
        Path timeline = Files.writeString(taskDirectory.resolve("timeline.json"), "{}");
        Path preview = Files.createDirectories(taskDirectory.resolve("render-preview"));
        Files.write(preview.resolve("frame-001.jpg"), new byte[]{1, 2, 3});
        new ObjectMapper().writeValue(preview.resolve("manifest.json").toFile(), Map.of("version", 1, "frames",
                java.util.List.of(Map.of("index", 1, "sequence", 3, "outputStartSeconds", 5.0,
                        "outputEndSeconds", 8.5, "fileName", "frame-001.jpg"))));

        VideoTask task = mock(VideoTask.class);
        VideoTaskRepository repository = mock(VideoTaskRepository.class);
        when(repository.findById(taskId)).thenReturn(Optional.of(task));
        TaskArtifactLocator artifacts = mock(TaskArtifactLocator.class);
        when(artifacts.latest(taskId, "TIMELINE_MANIFEST")).thenReturn(Optional.of(timeline));
        RenderPreviewService service = new RenderPreviewService(
                repository, new ObjectMapper(), storageRoot.toString(), artifacts);

        assertThat(service.frames(taskId)).singleElement().satisfies(frame -> {
            assertThat(frame.sequence()).isEqualTo(3);
            assertThat(frame.imageUrl()).endsWith("/render-preview/1");
        });
        assertThat(service.frame(taskId, 1)).isEqualTo(preview.resolve("frame-001.jpg").toAbsolutePath().normalize());
    }
}
