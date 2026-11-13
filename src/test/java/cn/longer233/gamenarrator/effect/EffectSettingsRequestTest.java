package cn.longer233.gamenarrator.effect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EffectSettingsRequestTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void normalizesMissingBooleanSwitchesFromPartialJson() throws Exception {
        EffectSettingsRequest settings = objectMapper.readValue(
                "{\"presetCode\":\"ANIME_THEATER\",\"brightness\":0.2}", EffectSettingsRequest.class);

        assertThat(settings.dynamicSubtitles()).isFalse();
        assertThat(settings.burnSubtitles()).isFalse();
        assertThat(settings.soundEffects()).isFalse();
        assertThat(settings.useLut()).isFalse();
        assertThat(settings.keywordHighlights()).isTrue();
        assertThat(settings.brightness()).isEqualTo(0.2);
    }

    @Test
    void keepsExplicitBooleanSwitches() throws Exception {
        EffectSettingsRequest settings = objectMapper.readValue("{\"presetCode\":\"ANIME_THEATER\","
                + "\"dynamicSubtitles\":true,\"keywordHighlights\":false,\"burnSubtitles\":true,"
                + "\"soundEffects\":true,\"useLut\":true}", EffectSettingsRequest.class);

        assertThat(settings.dynamicSubtitles()).isTrue();
        assertThat(settings.keywordHighlights()).isFalse();
        assertThat(settings.burnSubtitles()).isTrue();
        assertThat(settings.soundEffects()).isTrue();
        assertThat(settings.useLut()).isTrue();
    }

    @Test
    void neverExposesNullLutFlagEvenWhenConstructedManually() {
        EffectSettingsRequest settings = new EffectSettingsRequest("ANIME_THEATER", null, null, null,
                null, null, null, null, null, null, null, null);

        assertThat(settings.useLut()).isNotNull();
        assertThat(settings.dynamicSubtitles()).isNotNull();
        assertThat(settings.keywordHighlights()).isNotNull();
        assertThat(settings.burnSubtitles()).isNotNull();
        assertThat(settings.soundEffects()).isNotNull();
    }
}
