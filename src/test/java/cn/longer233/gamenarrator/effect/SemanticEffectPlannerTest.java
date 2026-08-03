package cn.longer233.gamenarrator.effect;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticEffectPlannerTest {
    private final SemanticEffectPlanner planner = new SemanticEffectPlanner();

    @Test
    void mapsHighEnergyCueToImpactEffects() {
        EffectPlan plan = planner.plan("高燃冲击转场", "Boss 发起最后一击", 2);
        assertThat(plan.effects()).contains(VisualEffectType.ZOOM_PUNCH, VisualEffectType.WHITE_FLASH);
        assertThat(plan.transition()).isEqualTo(TransitionType.ANIME_IMPACT);
    }

    @Test
    void givesFirstSegmentAStableOpeningEffect() {
        EffectPlan plan = planner.plan("", "普通叙述", 1);
        assertThat(plan.effects()).contains(VisualEffectType.TITLE_CARD);
        assertThat(plan.transition()).isEqualTo(TransitionType.FADE);
    }

    @Test
    void mapsPremiereStyleCuesToImplementedEffects() {
        EffectPlan dream=planner.plan("梦境柔焦和冷色暗角","进入回忆",2);
        assertThat(dream.effects()).contains(VisualEffectType.GAUSSIAN_BLUR,VisualEffectType.VIGNETTE,
                VisualEffectType.COOL_TONE);
        EffectPlan glitch=planner.plan("RGB 故障像素马赛克","信号中断",3);
        assertThat(glitch.effects()).contains(VisualEffectType.RGB_SPLIT,VisualEffectType.PIXELATE);
    }
}
