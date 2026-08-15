package cn.longer233.gamenarrator.task.application;

import cn.longer233.gamenarrator.pipeline.TaskArtifactLocator;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.domain.ProcessingStageType;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class VideoTaskViewMapper {
    private final TaskArtifactLocator artifacts;

    public VideoTaskViewMapper(TaskArtifactLocator artifacts) {
        this.artifacts = artifacts;
    }

    public VideoTaskView from(VideoTask task) {
        Map<String, Path> visible = new HashMap<>(artifacts.latestAll(task.getId()));
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
