package cn.longer233.gamenarrator.effect;

import java.util.List;

public record EffectPlan(
        List<VisualEffectType> effects,
        TransitionType transition,
        String reason
) {
}
