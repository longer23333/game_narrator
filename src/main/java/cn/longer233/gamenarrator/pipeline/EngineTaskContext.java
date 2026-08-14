package cn.longer233.gamenarrator.pipeline;

import cn.longer233.gamenarrator.task.domain.ProcessingStageType;

import java.util.Set;

public record EngineTaskContext(
        String sourceVideoPath,
        Set<ProcessingStageType> completedStages,
        boolean hasAudio,
        String extractedAudioPath,
        String sceneManifestPath,
        String transcriptText,
        String visualAnalysisPath,
        String highlightManifestPath,
        String generatedScriptPath,
        String voiceManifestPath,
        String timelinePath,
        Double durationSeconds,
        int targetDurationSeconds,
        String editingScope,
        String gameCategory,
        String commentaryStyle,
        String taskBrief,
        boolean storyboardReviewEnabled,
        boolean storyboardApproved,
        boolean cloudVisionEnabled,
        boolean aiScriptEnabled,
        boolean aiVoiceEnabled,
        boolean autoAssetsEnabled,
        boolean automaticGenerationEnabled
) {
    public EngineTaskContext {
        completedStages = Set.copyOf(completedStages);
    }

    public boolean stageCompleted(ProcessingStageType stageType) {
        return completedStages.contains(stageType);
    }
}
