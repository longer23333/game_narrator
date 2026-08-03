package cn.longer233.gamenarrator.task.application;

import cn.longer233.gamenarrator.task.domain.CommentaryStyle;
import cn.longer233.gamenarrator.task.domain.EditingScope;
import jakarta.validation.constraints.*;

public record CreateVideoTaskCommand(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 40) String gameCategory,
        @NotNull CommentaryStyle commentaryStyle,
        @Min(15) @Max(3600) int targetDurationSeconds,
        @NotNull EditingScope editingScope,
        @NotBlank @Size(max = 500) String taskBrief,
        @Size(max = 4000) String terminologyGlossary,
        boolean storyboardReviewEnabled,
        boolean automaticGenerationEnabled,
        boolean cloudVisionEnabled,
        boolean aiScriptEnabled,
        boolean aiVoiceEnabled,
        boolean autoAssetsEnabled
) {
}
