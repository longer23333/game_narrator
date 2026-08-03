package cn.longer233.gamenarrator.effect;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

public record EffectSettingsRequest(
        @NotBlank String presetCode,
        @DecimalMin("0.0") @DecimalMax("1.0") Double intensity,
        Boolean dynamicSubtitles,
        Boolean soundEffects
) {
    public static EffectSettingsRequest defaults() {
        return new EffectSettingsRequest("ANIME_THEATER", null, true, false);
    }
}
