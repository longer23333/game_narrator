package cn.longer233.gamenarrator.script;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateStoryboardSegmentRequest(
        @DecimalMin("0.0") double startSeconds,
        @DecimalMin("0.01") double endSeconds,
        @NotBlank @Size(max = 500) String narration,
        @Size(max = 500) String subtitle,
        @Size(max = 200) String effectCue
) {
}
