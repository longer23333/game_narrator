package cn.longer233.gamenarrator.effect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EffectPresetCatalogTest {
    @TempDir Path storageRoot;

    @Test
    void customTemplatePersistsAndReloads() {
        EffectPresetCatalog catalog = new EffectPresetCatalog(new ObjectMapper(), storageRoot.toString());
        EffectPreset custom = new EffectPreset("CUSTOM_MY_STYLE", "我的风格", "用于分享的本地自定义模板。",
                .72, 3, .3, "CLEAN_WHITE", .2,
                List.of(VisualEffectType.WARM_TONE, VisualEffectType.TITLE_CARD),
                List.of(TransitionType.FADE, TransitionType.HARD_CUT));

        assertThat(catalog.all()).hasSize(13);
        catalog.importTemplate(custom);
        assertThat(catalog.require("custom_my_style").name()).isEqualTo("我的风格");

        EffectPresetCatalog reloaded = new EffectPresetCatalog(new ObjectMapper(), storageRoot.toString());
        assertThat(reloaded.all()).hasSize(14);
        assertThat(reloaded.require("CUSTOM_MY_STYLE").preferredEffects()).contains(VisualEffectType.WARM_TONE);
    }

    @Test
    void builtInTemplateCannotBeOverwrittenAndRangesAreValidated() {
        EffectPresetCatalog catalog = new EffectPresetCatalog(new ObjectMapper(), storageRoot.toString());
        EffectPreset overwrite = new EffectPreset("ANIME_THEATER", "覆盖", "不允许覆盖内置模板。",
                .5, 2, .2, "CLEAN_WHITE", .2, List.of(VisualEffectType.TITLE_CARD), List.of(TransitionType.FADE));
        assertThatThrownBy(() -> catalog.importTemplate(overwrite)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能覆盖");
        EffectPreset invalid = new EffectPreset("CUSTOM_INVALID", "越界", "参数超出允许范围。",
                2, 20, 5, "", 2, List.of(VisualEffectType.TITLE_CARD), List.of(TransitionType.FADE));
        assertThatThrownBy(() -> catalog.importTemplate(invalid)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("默认强度");
    }
}
