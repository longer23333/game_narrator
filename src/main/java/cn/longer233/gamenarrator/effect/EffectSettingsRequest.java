package cn.longer233.gamenarrator.effect;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EffectSettingsRequest(
        @NotBlank String presetCode,
        @DecimalMin("0.0") @DecimalMax("1.0") Double intensity,
        Boolean dynamicSubtitles,
        Boolean keywordHighlights,
        Boolean burnSubtitles,
        @Size(max = 40) String subtitleTemplate,
        Boolean soundEffects,
        @DecimalMin("-1.0") @DecimalMax("1.0") Double brightness,
        @DecimalMin("0.0") @DecimalMax("3.0") Double contrast,
        @DecimalMin("0.0") @DecimalMax("3.0") Double saturation,
        @DecimalMin("-1.0") @DecimalMax("1.0") Double temperature,
        Boolean useLut
) {
    public static EffectSettingsRequest defaults() {
        return new EffectSettingsRequest("ANIME_THEATER", null, true, true, true, null,
                false, 0d, 1d, 1d, 0d, false);
    }
}
