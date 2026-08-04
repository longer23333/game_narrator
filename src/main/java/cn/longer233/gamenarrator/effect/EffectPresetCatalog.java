package cn.longer233.gamenarrator.effect;

import cn.longer233.gamenarrator.common.AtomicArtifactWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EffectPresetCatalog {
    private static final Logger log = LoggerFactory.getLogger(EffectPresetCatalog.class);
    private final List<EffectPreset> builtIn = List.of(
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
                    List.of(TransitionType.DISSOLVE, TransitionType.FADE)),
            new EffectPreset("BATTLE_ROYALE", "吃鸡风格",
                    "快速搜点、交火和决赛圈冲击，突出击倒、转点与胜利时刻。",
                    0.86, 3, 0.16, "IMPACT_RED", 0.14,
                    List.of(VisualEffectType.ZOOM_PUNCH, VisualEffectType.SPEED_LINES,
                            VisualEffectType.HIGH_CONTRAST, VisualEffectType.WHITE_FLASH),
                    List.of(TransitionType.HARD_CUT, TransitionType.PUSH, TransitionType.ANIME_IMPACT)),
            new EffectPreset("SOULS_LIKE", "魂系风格",
                    "低饱和、暗角与电影黑边强化压迫感，适合 Boss 战与克制叙事。",
                    0.68, 3, 0.42, "TYPEWRITER_DARK", 0.12,
                    List.of(VisualEffectType.VIGNETTE, VisualEffectType.CINEMA_BARS,
                            VisualEffectType.HIGH_CONTRAST, VisualEffectType.SLOW_MOTION),
                    List.of(TransitionType.HARD_CUT, TransitionType.FADE, TransitionType.DISSOLVE)),
            new EffectPreset("MOE_ANIME", "二次元萌系风格",
                    "明快色彩、弹跳字幕与轻量定格，适合可爱角色和轻松日常内容。",
                    0.64, 2, 0.24, "COMEDY_POP", 0.18,
                    List.of(VisualEffectType.TITLE_CARD, VisualEffectType.FREEZE_ACCENT,
                            VisualEffectType.WARM_TONE, VisualEffectType.ZOOM_PUNCH),
                    List.of(TransitionType.FADE, TransitionType.PUSH, TransitionType.HARD_CUT))
    );

    private final Map<String, EffectPreset> custom = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Path customManifest;

    public EffectPresetCatalog(ObjectMapper objectMapper, @Value("${game-narrator.storage-root}") String storageRoot) {
        this.objectMapper = objectMapper;
        this.customManifest = Path.of(storageRoot).toAbsolutePath().normalize()
                .resolve("style-templates").resolve("custom-templates.json");
        loadCustom();
    }

    public List<EffectPreset> all() {
        List<EffectPreset> result = new ArrayList<>(builtIn);
        custom.values().stream().sorted(Comparator.comparing(EffectPreset::name)).forEach(result::add);
        return List.copyOf(result);
    }

    public EffectPreset require(String code) {
        return all().stream().filter(item -> item.code().equalsIgnoreCase(code))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("未知特效预设：" + code));
    }

    public synchronized EffectPreset importTemplate(EffectPreset value) {
        EffectPreset normalized = validate(value);
        if (builtIn.stream().anyMatch(item -> item.code().equals(normalized.code()))) {
            throw new IllegalArgumentException("不能覆盖内置风格模板：" + normalized.code());
        }
        EffectPreset previous = custom.put(normalized.code(), normalized);
        try { persist(); }
        catch (RuntimeException exception) {
            if (previous == null) custom.remove(normalized.code()); else custom.put(normalized.code(), previous);
            throw exception;
        }
        return normalized;
    }

    private EffectPreset validate(EffectPreset value) {
        if (value == null) throw new IllegalArgumentException("模板 JSON 不能为空");
        String code = value.code() == null ? "" : value.code().trim().toUpperCase(Locale.ROOT);
        String name = value.name() == null ? "" : value.name().trim();
        String description = value.description() == null ? "" : value.description().trim();
        if (!code.matches("[A-Z][A-Z0-9_]{2,39}")) throw new IllegalArgumentException("模板 code 必须为 3-40 位大写字母、数字或下划线");
        if (name.isBlank() || name.length() > 60) throw new IllegalArgumentException("模板名称长度必须为 1-60 个字符");
        if (description.isBlank() || description.length() > 300) throw new IllegalArgumentException("模板说明长度必须为 1-300 个字符");
        if (value.defaultIntensity() < 0 || value.defaultIntensity() > 1) throw new IllegalArgumentException("默认强度必须在 0-1 之间");
        if (value.maxEffectsPerClip() < 1 || value.maxEffectsPerClip() > 6) throw new IllegalArgumentException("每片段特效数量必须在 1-6 之间");
        if (value.transitionDurationSeconds() < 0 || value.transitionDurationSeconds() > 3) throw new IllegalArgumentException("转场时长必须在 0-3 秒之间");
        if (value.sourceAudioVolume() < 0 || value.sourceAudioVolume() > 1) throw new IllegalArgumentException("原声音量必须在 0-1 之间");
        if (value.subtitleTheme() == null || value.subtitleTheme().isBlank() || value.subtitleTheme().length() > 40) throw new IllegalArgumentException("字幕主题不能为空");
        if (value.preferredEffects() == null || value.preferredEffects().isEmpty() || value.preferredEffects().size() > 10) throw new IllegalArgumentException("模板必须包含 1-10 个视觉特效");
        if (value.allowedTransitions() == null || value.allowedTransitions().isEmpty() || value.allowedTransitions().size() > 6) throw new IllegalArgumentException("模板必须包含 1-6 个转场");
        return new EffectPreset(code, name, description, value.defaultIntensity(), value.maxEffectsPerClip(),
                value.transitionDurationSeconds(), value.subtitleTheme().trim(), value.sourceAudioVolume(),
                List.copyOf(value.preferredEffects()), List.copyOf(value.allowedTransitions()));
    }

    private void loadCustom() {
        if (!Files.isRegularFile(customManifest)) return;
        try {
            var root = objectMapper.readTree(customManifest.toFile());
            List<EffectPreset> values = objectMapper.readerForListOf(EffectPreset.class).readValue(root.path("templates"));
            values.forEach(value -> { EffectPreset normalized = validate(value); custom.put(normalized.code(), normalized); });
        } catch (Exception exception) {
            log.warn("STYLE_TEMPLATE_LOAD_FAILED path={} message={}", customManifest, exception.getMessage());
        }
    }

    private void persist() {
        try {
            List<EffectPreset> values = custom.values().stream().sorted(Comparator.comparing(EffectPreset::code)).toList();
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("version", 1); document.put("templates", values);
            AtomicArtifactWriter.writeJson(objectMapper, customManifest, document);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("无法保存自定义风格模板", exception);
        }
    }
}
