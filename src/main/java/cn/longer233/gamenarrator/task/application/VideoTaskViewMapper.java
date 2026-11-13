package cn.longer233.gamenarrator.task.application;

import cn.longer233.gamenarrator.pipeline.TaskArtifactLocator;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.domain.ProcessingStageType;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class VideoTaskViewMapper {
    private final TaskArtifactLocator artifacts;

    public VideoTaskViewMapper(TaskArtifactLocator artifacts) {
        this.artifacts = artifacts;
    }

    public VideoTaskView from(VideoTask task) {
        return from(task, artifacts.latestAll(task.getId()));
    }

    /** 列表场景：一次批量取回所有任务的产物，避免每任务一次查询。 */
    public List<VideoTaskView> fromAll(List<VideoTask> tasks) {
        if (tasks == null || tasks.isEmpty()) return List.of();
        Map<UUID, Map<String, Path>> byTask = artifacts.latestAllByTaskIds(
                tasks.stream().map(VideoTask::getId).toList());
        return tasks.stream()
                .map(task -> from(task, byTask.getOrDefault(task.getId(), Map.of())))
                .toList();
    }

    private VideoTaskView from(VideoTask task, Map<String, Path> artifactPaths) {
        Map<String, Path> visible = new HashMap<>(artifactPaths);
        retainWhenCompleted(task, visible, ProcessingStageType.SCENE_DETECTION,
                "EXTRACTED_AUDIO", "SCENE_MANIFEST");
        retainWhenCompleted(task, visible, ProcessingStageType.TRANSCRIPTION,
                "TRANSCRIPT_TEXT", "TRANSCRIPT_SUBTITLE", "TRANSCRIPT_DETAIL");
        retainWhenCompleted(task, visible, ProcessingStageType.VIDEO_UNDERSTANDING, "VISION_ANALYSIS");
        retainWhenCompleted(task, visible, ProcessingStageType.HIGHLIGHT_SELECTION, "HIGHLIGHT_MANIFEST");
        retainWhenCompleted(task, visible, ProcessingStageType.SCRIPT_GENERATION, "SCRIPT_MANIFEST");
        retainWhenCompleted(task, visible, ProcessingStageType.VOICE_GENERATION, "VOICE_MANIFEST");
        retainWhenCompleted(task, visible, ProcessingStageType.TIMELINE_PLANNING, "TIMELINE_MANIFEST");
        retainWhenCompleted(task, visible, ProcessingStageType.RENDERING,
                "RENDERED_VIDEO", "GENERATED_SUBTITLE");
        return VideoTaskView.from(task, Map.copyOf(visible));
    }

    private void retainWhenCompleted(VideoTask task, Map<String, Path> visible,
                                     ProcessingStageType stage, String... artifactTypes) {
        if (task.isStageCompleted(stage)) return;
        Set.of(artifactTypes).forEach(visible::remove);
    }
}
