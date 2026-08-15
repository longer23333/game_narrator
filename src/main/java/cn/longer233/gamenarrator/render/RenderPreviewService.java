package cn.longer233.gamenarrator.render;

import cn.longer233.gamenarrator.task.application.TaskNotFoundException;
import cn.longer233.gamenarrator.pipeline.TaskArtifactLocator;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class RenderPreviewService {
    private final VideoTaskRepository tasks;
    private final ObjectMapper objectMapper;
    private final Path storageRoot;
    private final TaskArtifactLocator artifacts;

    public RenderPreviewService(VideoTaskRepository tasks, ObjectMapper objectMapper,
            @Value("${game-narrator.storage-root}") String storageRoot, TaskArtifactLocator artifacts) {
        this.tasks = tasks;
        this.objectMapper = objectMapper;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.artifacts = artifacts;
    }

    public List<PreviewFrame> frames(UUID taskId) {
        Path directory = previewDirectory(taskId);
        Path manifest = directory.resolve("manifest.json");
        if (!Files.isRegularFile(manifest)) return List.of();
        try {
            var root = objectMapper.readTree(manifest.toFile());
            return objectMapper.readerForListOf(ManifestFrame.class).<List<ManifestFrame>>readValue(root.path("frames"))
                    .stream().map(frame -> new PreviewFrame(frame.index(), frame.sequence(),
                            frame.outputStartSeconds(), frame.outputEndSeconds(),
                            "/api/tasks/" + taskId + "/render-preview/" + frame.index())).toList();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("无法读取渲染预览清单", exception);
        }
    }

    public Path frame(UUID taskId, int index) {
        if (index < 1 || index > 999) throw new IllegalArgumentException("预览帧编号无效");
        Path frame = previewDirectory(taskId).resolve("frame-%03d.jpg".formatted(index)).normalize();
        if (!frame.startsWith(storageRoot) || !Files.isRegularFile(frame)) {
            throw new IllegalStateException("渲染预览帧不存在");
        }
        return frame;
    }

    private Path previewDirectory(UUID taskId) {
        tasks.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
        Path timeline = artifacts.latest(taskId, "TIMELINE_MANIFEST")
                .orElse(null);
        if (timeline == null) return storageRoot.resolve("unavailable");
        Path directory = timeline.getParent().resolve("render-preview").normalize();
        if (!timeline.startsWith(storageRoot) || !directory.startsWith(storageRoot)) {
            throw new IllegalStateException("渲染预览目录不属于任务存储目录");
        }
        return directory;
    }

    private record ManifestFrame(int index, int sequence, double outputStartSeconds,
                                 double outputEndSeconds, String fileName) { }
    public record PreviewFrame(int index, int sequence, double outputStartSeconds,
                               double outputEndSeconds, String imageUrl) { }
}
