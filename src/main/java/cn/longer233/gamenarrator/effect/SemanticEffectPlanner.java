package cn.longer233.gamenarrator.effect;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class SemanticEffectPlanner {

    public EffectPlan plan(String cue, String narration, int sequence) {
        return plan(cue, narration, sequence, null);
    }

    public EffectPlan plan(String cue, String narration, int sequence, EffectPreset preset) {
        String text = ((cue == null ? "" : cue) + " " + (narration == null ? "" : narration))
                .toLowerCase(Locale.ROOT);
        List<VisualEffectType> effects = new ArrayList<>();
        TransitionType transition = TransitionType.HARD_CUT;
        List<String> reasons = new ArrayList<>();

        if (contains(text, "冲击", "高燃", "击杀", "爆发", "反转", "impact")) {
            add(effects, VisualEffectType.ZOOM_PUNCH, VisualEffectType.WHITE_FLASH);
            transition = TransitionType.ANIME_IMPACT;
            reasons.add("冲击/高燃语义");
        }
        if (contains(text, "震动", "爆炸", "重击", "boss", "战斗")) {
            add(effects, VisualEffectType.CAMERA_SHAKE);
            reasons.add("战斗语义");
        }
        if (contains(text, "速度线", "冲刺", "加速", "追击", "疾驰")) {
            add(effects, VisualEffectType.SPEED_LINES);
            transition = TransitionType.PUSH;
            reasons.add("高速运动语义");
        }
        if (contains(text, "慢放", "慢动作", "悬念", "紧张", "凝固")) {
            add(effects, VisualEffectType.SLOW_MOTION, VisualEffectType.CINEMA_BARS);
            transition = TransitionType.DISSOLVE;
            reasons.add("悬念/慢动作语义");
        }
        if (contains(text, "定格", "搞笑", "吐槽", "尴尬", "失败")) {
            add(effects, VisualEffectType.FREEZE_ACCENT);
            reasons.add("定格强调语义");
        }
        if (contains(text, "标题", "登场", "章节", "人物介绍", "开场")) {
            add(effects, VisualEffectType.TITLE_CARD);
            transition = TransitionType.FADE;
            reasons.add("标题/登场语义");
        }
        if (contains(text, "模糊", "柔焦", "梦境", "回忆", "blur")) {
            add(effects, VisualEffectType.GAUSSIAN_BLUR, VisualEffectType.VIGNETTE);
            transition = TransitionType.DISSOLVE;
            reasons.add("模糊/梦境语义");
        }
        if (contains(text, "黑白", "单色", "往事", "black and white")) {
            add(effects, VisualEffectType.BLACK_AND_WHITE);
            reasons.add("黑白语义");
        }
        if (contains(text, "暖色", "夕阳", "温暖", "怀旧")) {
            add(effects, VisualEffectType.WARM_TONE, VisualEffectType.VIGNETTE);
            reasons.add("暖色语义");
        }
        if (contains(text, "冷色", "冰冷", "科技", "夜晚")) {
            add(effects, VisualEffectType.COOL_TONE);
            reasons.add("冷色语义");
        }
        if (contains(text, "故障", "干扰", "rgb", "像素", "马赛克", "glitch")) {
            add(effects, VisualEffectType.RGB_SPLIT, VisualEffectType.PIXELATE);
            transition = TransitionType.HARD_CUT;
            reasons.add("数字故障语义");
        }
        if (contains(text, "镜像", "翻转", "反向")) {
            add(effects, VisualEffectType.HORIZONTAL_FLIP);
            reasons.add("镜像语义");
        }
        if (effects.isEmpty()) {
            if (sequence == 1) {
                effects.add(VisualEffectType.TITLE_CARD);
                transition = TransitionType.FADE;
                reasons.add("首段默认开场");
            } else {
                effects.add(VisualEffectType.ZOOM_PUNCH);
                reasons.add("节奏转场默认强调");
            }
        }
        if (preset != null) {
            effects.removeIf(effect -> !preset.preferredEffects().contains(effect));
            if (effects.isEmpty() && !preset.preferredEffects().isEmpty()) {
                effects.add(preset.preferredEffects().getFirst());
                reasons.add("预设默认效果");
            }
            while (effects.size() > preset.maxEffectsPerClip()) effects.remove(effects.size() - 1);
            if ("PASSIONATE".equals(preset.code()) && reasons.contains("预设默认效果")
                    && preset.allowedTransitions().contains(TransitionType.ANIME_IMPACT)) {
                transition = TransitionType.ANIME_IMPACT;
            }
            if (!preset.allowedTransitions().contains(transition)) {
                transition = preset.allowedTransitions().getFirst();
            }
            reasons.add("预设：" + preset.name());
        }
        return new EffectPlan(List.copyOf(effects), transition, String.join("、", reasons));
    }

    private boolean contains(String value, String... words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }

    private void add(List<VisualEffectType> target, VisualEffectType... values) {
        for (VisualEffectType value : values) if (!target.contains(value)) target.add(value);
    }
}
