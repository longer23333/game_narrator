package cn.longer233.gamenarrator.effect;

import java.util.List;

public record EffectPreset(
        String code,
        String name,
        String description,
        double defaultIntensity,
        int maxEffectsPerClip,
        double transitionDurationSeconds,
        String subtitleTheme,
        double sourceAudioVolume,
        List<VisualEffectType> preferredEffects,
        List<TransitionType> allowedTransitions
) {
}
