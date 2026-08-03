package cn.longer233.gamenarrator.script;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateStoryboardAssetRequest(
        @Pattern(regexp = "TOP_LEFT|TOP_RIGHT|CENTER|BOTTOM_LEFT|BOTTOM_RIGHT|FULL_SCREEN|AUDIO_TRACK") String position,
        boolean cutoutApplied,
        @Size(max = 500) String instruction) { }
