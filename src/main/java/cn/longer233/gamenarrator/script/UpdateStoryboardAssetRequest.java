package cn.longer233.gamenarrator.script;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateStoryboardAssetRequest(
        @Pattern(regexp = "TOP_LEFT|TOP_RIGHT|CENTER|BOTTOM_LEFT|BOTTOM_RIGHT|FULL_SCREEN|AUDIO_TRACK") String position,
        boolean cutoutApplied,
        @Size(max = 500) String instruction,
        @DecimalMin("0.0") Double startOffsetSeconds,
        @DecimalMin("0.0") Double endOffsetSeconds,
        @Min(5) @Max(200) Integer scalePercent,
        @Pattern(regexp = "NONE|FADE|POP|SLIDE|BOUNCE") String animation,
        @Min(-100) @Max(100) Integer zIndex) {
    public UpdateStoryboardAssetRequest(String position, boolean cutoutApplied, String instruction) {
        this(position, cutoutApplied, instruction, 0d, null, 38, "NONE", 0);
    }
}
