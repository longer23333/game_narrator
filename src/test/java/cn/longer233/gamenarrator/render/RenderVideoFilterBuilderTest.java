package cn.longer233.gamenarrator.render;

import cn.longer233.gamenarrator.effect.EffectPlan;
import cn.longer233.gamenarrator.effect.TransitionType;
import cn.longer233.gamenarrator.effect.VisualEffectType;
import cn.longer233.gamenarrator.effect.EffectSettingsRequest;
import cn.longer233.gamenarrator.timeline.TimelineSegment;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RenderVideoFilterBuilderTest {
    private final RenderVideoFilterBuilder filterBuilder = new RenderVideoFilterBuilder();

    @Test
    void buildsStableFiltersForImpactAndCinemaEffects() {
        TimelineSegment segment = new TimelineSegment(1, 0, 12, 3, 15,
                "最后一击", "最后一击", "高燃冲击", "voice.wav", 2, false);
        EffectPlan plan = new EffectPlan(List.of(VisualEffectType.ZOOM_PUNCH,
                VisualEffectType.WHITE_FLASH, VisualEffectType.CINEMA_BARS), TransitionType.ANIME_IMPACT, "test");

        assertThat(filterBuilder.video(segment, plan))
                .contains("crop=1920:1080", "color=white", "drawbox", "format=yuv420p");
    }

    @Test
    void buildsCutoutStoryboardOverlayAtRequestedPosition() {
        TimelineSegment segment = new TimelineSegment(1, 0, 12, 3, 15,
                "narration", "subtitle", "impact", "voice.wav", 2, false);
        var asset = new RenderAssetResolver.RenderAsset(1, "VIDEO", "OVERLAY", "BOTTOM_RIGHT",
                true, Path.of("overlay.webm"), "overlay", 0, null, 38, "NONE", 0);

        String graph = filterBuilder.storyboard(segment,
                new EffectPlan(List.of(), TransitionType.HARD_CUT, "test"), null, List.of(asset), 1);

        assertThat(graph).contains("chromakey=0x00FF00", "overlay=W-w-40:H-h-40", "[vout]");
    }

    @Test
    void parameterizesMemeWindowScaleAnimationAndLayerOrder() {
        TimelineSegment segment = new TimelineSegment(1, 0, 6, 0, 6,
                "narration", "subtitle", "", "voice.wav", 2, false);
        var lower = new RenderAssetResolver.RenderAsset(1, "MEME", "OVERLAY", "TOP_LEFT",
                false, Path.of("lower.png"), "lower", .5, 4.5, 25, "FADE", 1);
        var upper = new RenderAssetResolver.RenderAsset(1, "MEME", "OVERLAY", "BOTTOM_RIGHT",
                false, Path.of("upper.webp"), "upper", 1.25, 3.75, 42, "BOUNCE", 9);

        String graph = filterBuilder.storyboard(segment,
                new EffectPlan(List.of(), TransitionType.HARD_CUT, "test"), null, List.of(lower, upper), 1);

        assertThat(graph).contains("scale=480:-2", "fade=t=in:st=0.500:d=0.200:alpha=1",
                "between(t,0.500,4.500)", "scale=806:-2", "18*abs(sin(8*(t-1.250)))",
                "between(t,1.250,3.750)", "[base][asset0]", "[v0][asset1]");
    }

    @Test
    void buildsFiltersForCommonPremiereStyleEffects() {
        TimelineSegment segment = new TimelineSegment(1, 0, 8, 0, 8,
                "回忆故障", "回忆故障", "梦境故障", "voice.wav", 2, false);
        EffectPlan plan = new EffectPlan(List.of(VisualEffectType.GAUSSIAN_BLUR,
                VisualEffectType.VIGNETTE, VisualEffectType.COOL_TONE, VisualEffectType.RGB_SPLIT,
                VisualEffectType.PIXELATE, VisualEffectType.LENS_DISTORTION), TransitionType.DISSOLVE, "test");

        String graph = filterBuilder.video(segment, plan);
        assertThat(graph).contains("gblur=", "vignette=", "colorbalance=",
                "rgbashift=", "flags=neighbor", "lenscorrection=");
        assertThat(graph).doesNotContain("fade=t=in", "fade=t=out");
    }

    @Test
    void appliesColorWheelAndEscapedCubeLut() {
        TimelineSegment segment = new TimelineSegment(1, 0, 8, 0, 8,
                "调色", "调色", "", "voice.wav", 2, false);
        EffectSettingsRequest settings = new EffectSettingsRequest(
                "ANIME_THEATER", .5, true, true, true, "ANIME_OUTLINE",
                false, .1, 1.2, .8, -.5, true);

        Path lutPath = Path.of("C:/project/color-lut.cube");
        String filter = filterBuilder.video(segment,
                new EffectPlan(List.of(), TransitionType.HARD_CUT, "test"), null, settings,
                lutPath);

        String escapedLutPath = lutPath.toAbsolutePath().toString().replace('\\', '/')
                .replace(":", "\\:").replace("'", "\\'");

        assertThat(filter).contains("eq=brightness=0.100:contrast=1.200:saturation=0.800",
                "colorbalance=rs=-0.060:bs=0.060", "lut3d=file='" + escapedLutPath + "'");
    }
}
