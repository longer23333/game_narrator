package cn.longer233.gamenarrator.effect;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EffectPresetCatalog {
    private final List<EffectPreset> presets = List.of(
            new EffectPreset("ANIME_THEATER", "动漫剧场",
                    "强调登场、反转和画面冲击，适合剧情向游戏与动漫解说。",
                    0.78, 3, 0.28, "ANIME_OUTLINE", 0.18,
                    List.of(VisualEffectType.TITLE_CARD, VisualEffectType.ZOOM_PUNCH,
                            VisualEffectType.WHITE_FLASH, VisualEffectType.CINEMA_BARS),
                    List.of(TransitionType.HARD_CUT, TransitionType.FADE,
                            TransitionType.DISSOLVE, TransitionType.ANIME_IMPACT)),
            new EffectPreset("PASSIONATE", "热血高燃",
                    "更高频的缩放、震动和闪白，适合 Boss 战、击杀和竞技高光。",
                    0.92, 3, 0.18, "IMPACT_RED", 0.14,
                    List.of(VisualEffectType.ZOOM_PUNCH, VisualEffectType.CAMERA_SHAKE,
                            VisualEffectType.WHITE_FLASH, VisualEffectType.SPEED_LINES),
                    List.of(TransitionType.HARD_CUT, TransitionType.PUSH, TransitionType.ANIME_IMPACT)),
            new EffectPreset("HUMOROUS", "轻松吐槽",
                    "以定格、局部放大和短停顿为主，减少强烈闪烁。",
                    0.66, 2, 0.22, "COMEDY_POP", 0.16,
                    List.of(VisualEffectType.FREEZE_ACCENT, VisualEffectType.ZOOM_PUNCH,
                            VisualEffectType.TITLE_CARD),
                    List.of(TransitionType.HARD_CUT, TransitionType.FADE, TransitionType.PUSH)),
            new EffectPreset("SUSPENSE", "悬疑叙事",
                    "慢节奏推进、暗角和电影黑边，适合恐怖与解谜内容。",
                    0.62, 2, 0.45, "TYPEWRITER_DARK", 0.12,
                    List.of(VisualEffectType.SLOW_MOTION, VisualEffectType.CINEMA_BARS,
                            VisualEffectType.TITLE_CARD),
                    List.of(TransitionType.FADE, TransitionType.DISSOLVE, TransitionType.HARD_CUT)),
            new EffectPreset("CLEAN", "简洁记录",
                    "保留自然画面，只使用轻量淡入和基础字幕。",
                    0.30, 1, 0.20, "CLEAN_WHITE", 0.24,
                    List.of(VisualEffectType.TITLE_CARD),
                    List.of(TransitionType.HARD_CUT, TransitionType.FADE)),
            new EffectPreset("PREMIERE_CINEMATIC", "PR · 电影质感",
                    "参考 Premiere 常见的 Lumetri 对比、暗角、电影黑边和柔和转场。",
                    0.58, 3, 0.42, "CLEAN_WHITE", 0.16,
                    List.of(VisualEffectType.HIGH_CONTRAST, VisualEffectType.VIGNETTE,
                            VisualEffectType.CINEMA_BARS, VisualEffectType.WARM_TONE),
                    List.of(TransitionType.DISSOLVE, TransitionType.FADE, TransitionType.HARD_CUT)),
            new EffectPreset("PREMIERE_DOCUMENTARY", "PR · 纪实清晰",
                    "保留真实画面，以轻微对比和冷色校正为主，减少强烈动态效果。",
                    0.34, 2, 0.24, "CLEAN_WHITE", 0.25,
                    List.of(VisualEffectType.HIGH_CONTRAST, VisualEffectType.COOL_TONE,
                            VisualEffectType.TITLE_CARD),
                    List.of(TransitionType.HARD_CUT, TransitionType.DISSOLVE)),
            new EffectPreset("PREMIERE_RETRO", "PR · 复古回忆",
                    "参考黑白、暖色、暗角与轻柔模糊的复古组合。",
                    0.55, 3, 0.38, "TYPEWRITER_DARK", 0.17,
                    List.of(VisualEffectType.WARM_TONE, VisualEffectType.VIGNETTE,
                            VisualEffectType.BLACK_AND_WHITE, VisualEffectType.GAUSSIAN_BLUR),
                    List.of(TransitionType.FADE, TransitionType.DISSOLVE)),
            new EffectPreset("PREMIERE_GLITCH", "PR · 数字故障",
                    "RGB 分离、镜头畸变、像素化与短促冲击，适合科技和故障段落。",
                    0.76, 3, 0.16, "IMPACT_RED", 0.13,
                    List.of(VisualEffectType.RGB_SPLIT, VisualEffectType.LENS_DISTORTION,
                            VisualEffectType.PIXELATE, VisualEffectType.CAMERA_SHAKE),
                    List.of(TransitionType.HARD_CUT, TransitionType.PUSH, TransitionType.ANIME_IMPACT)),
            new EffectPreset("PREMIERE_DREAM", "PR · 梦境柔焦",
                    "模糊、冷色与暗角组合，适合回忆、梦境和舒缓段落。",
                    0.48, 3, 0.48, "ANIME_OUTLINE", 0.12,
                    List.of(VisualEffectType.GAUSSIAN_BLUR, VisualEffectType.COOL_TONE,
                            VisualEffectType.VIGNETTE, VisualEffectType.SLOW_MOTION),
                    List.of(TransitionType.DISSOLVE, TransitionType.FADE))
    );

    public List<EffectPreset> all() {
        return presets;
    }

    public EffectPreset require(String code) {
        return presets.stream().filter(item -> item.code().equalsIgnoreCase(code))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("未知特效预设：" + code));
    }
}
