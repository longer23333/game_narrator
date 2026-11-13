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
    /**
     * 请求体允许省略任何开关（Jackson 反序列化后仍为 null），这里统一归一为渲染器的既有默认语义，
     * 避免下游出现 {@code Boolean.booleanValue()} 拆箱空指针。
     */
    public EffectSettingsRequest {
        dynamicSubtitles = Boolean.TRUE.equals(dynamicSubtitles);
        keywordHighlights = !Boolean.FALSE.equals(keywordHighlights);
        burnSubtitles = Boolean.TRUE.equals(burnSubtitles);
        soundEffects = Boolean.TRUE.equals(soundEffects);
        useLut = Boolean.TRUE.equals(useLut);
    }

    public static EffectSettingsRequest defaults() {
        return new EffectSettingsRequest("ANIME_THEATER", null, true, true, true, null,
                false, 0d, 1d, 1d, 0d, false);
    }
}
