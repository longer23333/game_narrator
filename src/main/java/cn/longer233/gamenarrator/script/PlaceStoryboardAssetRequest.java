package cn.longer233.gamenarrator.script;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PlaceStoryboardAssetRequest(
        @NotNull UUID assetId,
        @Size(max = 500) String instruction,
        boolean aiAssign,
        @DecimalMin("0.0") Double startOffsetSeconds,
        @DecimalMin("0.0") Double endOffsetSeconds,
        @Min(5) @Max(200) Integer scalePercent,
        @Pattern(regexp = "NONE|FADE|POP|SLIDE|BOUNCE") String animation,
        @Min(-100) @Max(100) Integer zIndex) {
    public PlaceStoryboardAssetRequest(UUID assetId, String instruction, boolean aiAssign) {
        this(assetId, instruction, aiAssign, 0d, null, 38, "NONE", 0);
    }
}
