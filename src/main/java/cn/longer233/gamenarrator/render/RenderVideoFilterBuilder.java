package cn.longer233.gamenarrator.render;

import cn.longer233.gamenarrator.effect.EffectPlan;
import cn.longer233.gamenarrator.effect.EffectPreset;
import cn.longer233.gamenarrator.effect.EffectSettingsRequest;
import cn.longer233.gamenarrator.effect.TransitionType;
import cn.longer233.gamenarrator.effect.VisualEffectType;
import cn.longer233.gamenarrator.timeline.TimelineSegment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.nio.file.Path;

/** Builds deterministic FFmpeg video-filter graphs independently from render process orchestration. */
@Component
public class RenderVideoFilterBuilder {
    public String storyboard(TimelineSegment segment, EffectPlan plan, EffectPreset preset,
                             List<RenderAssetResolver.RenderAsset> assets, int firstInput) {
        return storyboard(segment, plan, preset, assets, firstInput, null, null);
    }

    public String storyboard(TimelineSegment segment, EffectPlan plan, EffectPreset preset,
                             List<RenderAssetResolver.RenderAsset> assets, int firstInput,
                             EffectSettingsRequest settings, Path lutPath) {
        StringBuilder graph = new StringBuilder("[0:v]").append(video(segment, plan, preset, settings, lutPath)).append("[base];");
        String previous = "base";
        for (int index = 0; index < assets.size(); index++) {
            RenderAssetResolver.RenderAsset asset = assets.get(index);
            String prepared = "asset" + index;
            boolean background = "BACKGROUND".equals(asset.placementType());
            graph.append('[').append(firstInput + index).append(":v]")
                    .append("setpts=PTS-STARTPTS,");
            if (background) graph.append("scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080,")
                    .append("format=rgba,colorchannelmixer=aa=0.38");
            else graph.append("scale=").append(even(1920 * asset.scalePercent() / 100d))
                    .append(":-2:force_original_aspect_ratio=decrease,format=rgba")
                    .append(animationFilter(asset));
            if (asset.cutoutApplied()) graph.append(",chromakey=0x00FF00:0.18:0.08");
            graph.append('[').append(prepared).append("];[").append(previous).append("][")
                    .append(prepared).append("]overlay=").append(overlayPosition(asset))
                    .append(":enable='").append(enable(asset, segment))
                    .append("':eof_action=pass:shortest=0[v").append(index).append("];");
            previous = "v" + index;
        }
        graph.append('[').append(previous).append("]null[vout]");
        return graph.toString();
    }

    public String video(TimelineSegment segment, EffectPlan plan) {
        return video(segment, plan, null);
    }

    public String video(TimelineSegment segment, EffectPlan plan, EffectPreset preset) {
        return video(segment, plan, preset, null, null);
    }

    public String video(TimelineSegment segment, EffectPlan plan, EffectPreset preset,
                        EffectSettingsRequest settings, Path lutPath) {
        double duration = Math.max(0.5, segment.sourceEndSeconds() - segment.sourceStartSeconds());
        double intensity = preset == null ? 0.75 : preset.defaultIntensity();
        double transitionDuration = preset == null ? 0.28 : preset.transitionDurationSeconds();
        List<String> filters = new ArrayList<>();
        filters.add("scale=1920:1080:force_original_aspect_ratio=decrease");
        filters.add("pad=1920:1080:(ow-iw)/2:(oh-ih)/2:black");
        filters.add("setsar=1");
        if (plan.effects().contains(VisualEffectType.ZOOM_PUNCH)) {
            int zoomWidth = even(1920 * (1 + 0.10 * intensity));
            int zoomHeight = even(1080 * (1 + 0.10 * intensity));
            int offsetX = (zoomWidth - 1920) / 2;
            int offsetY = (zoomHeight - 1080) / 2;
            filters.add("scale=" + zoomWidth + ":" + zoomHeight);
            filters.add("crop=1920:1080:x='" + offsetX + "+" + decimal(24 * intensity)
                    + "*sin(2*PI*t/" + decimal(duration) + ")':y='" + offsetY + "+"
                    + decimal(14 * intensity) + "*sin(2*PI*t/" + decimal(duration) + ")'");
        }
        if (plan.effects().contains(VisualEffectType.CAMERA_SHAKE)) {
            filters.add("scale=1960:1120");
            filters.add("crop=1920:1080:x='20+" + decimal(12 * intensity)
                    + "*sin(45*t)':y='20+" + decimal(10 * intensity) + "*cos(39*t)'");
        }
        if (plan.effects().contains(VisualEffectType.WHITE_FLASH))
            filters.add("fade=t=in:st=0:d=" + decimal(0.08 + 0.08 * intensity) + ":color=white");
        if (plan.effects().contains(VisualEffectType.SLOW_MOTION)) filters.add("tmix=frames=3:weights='1 1 1'");
        if (plan.effects().contains(VisualEffectType.FREEZE_ACCENT)) {
            filters.add("eq=saturation=0.75:contrast=1.18"); filters.add("unsharp=5:5:1.2");
        }
        if (plan.effects().contains(VisualEffectType.SPEED_LINES)) {
            filters.add("vignette=PI/5"); filters.add("unsharp=7:7:1.5");
        }
        if (plan.effects().contains(VisualEffectType.CINEMA_BARS)) {
            filters.add("drawbox=x=0:y=0:w=iw:h=70:color=black:t=fill");
            filters.add("drawbox=x=0:y=ih-70:w=iw:h=70:color=black:t=fill");
        }
        if (plan.effects().contains(VisualEffectType.TITLE_CARD))
            filters.add("drawbox=x=0:y=0:w=iw:h=ih:color=black@0.22:t=fill:enable='between(t,0,0.8)'");
        if (plan.effects().contains(VisualEffectType.GAUSSIAN_BLUR)) filters.add("gblur=sigma=" + decimal(0.5 + 1.8 * intensity));
        if (plan.effects().contains(VisualEffectType.VIGNETTE)) filters.add("vignette=angle='PI/2.8'");
        if (plan.effects().contains(VisualEffectType.BLACK_AND_WHITE)) filters.add("hue=s=0");
        if (plan.effects().contains(VisualEffectType.WARM_TONE))
            filters.add("colorbalance=rs=" + decimal(0.08 * intensity) + ":bs=-" + decimal(0.06 * intensity));
        if (plan.effects().contains(VisualEffectType.COOL_TONE))
            filters.add("colorbalance=rs=-" + decimal(0.05 * intensity) + ":bs=" + decimal(0.08 * intensity));
        if (plan.effects().contains(VisualEffectType.HIGH_CONTRAST))
            filters.add("eq=contrast=" + decimal(1 + 0.28 * intensity) + ":saturation=" + decimal(1 + 0.10 * intensity));
        if (plan.effects().contains(VisualEffectType.RGB_SPLIT))
            filters.add("rgbashift=rh=" + Math.max(1, Math.round(5 * intensity)) + ":bh=-" + Math.max(1, Math.round(4 * intensity)));
        if (plan.effects().contains(VisualEffectType.HORIZONTAL_FLIP)) filters.add("hflip");
        if (plan.effects().contains(VisualEffectType.PIXELATE)) {
            int width = even(1920 - (1500 * intensity)); int height = even(1080 - (840 * intensity));
            filters.add("scale=" + Math.max(320, width) + ":" + Math.max(180, height) + ":flags=neighbor");
            filters.add("scale=1920:1080:flags=neighbor");
        }
        if (plan.effects().contains(VisualEffectType.LENS_DISTORTION))
            filters.add("lenscorrection=k1=" + decimal(-0.12 * intensity) + ":k2=" + decimal(0.04 * intensity));
        if (settings != null) {
            double brightness = settings.brightness() == null ? 0 : settings.brightness();
            double contrast = settings.contrast() == null ? 1 : settings.contrast();
            double saturation = settings.saturation() == null ? 1 : settings.saturation();
            if (brightness != 0 || contrast != 1 || saturation != 1) {
                filters.add("eq=brightness=" + decimal(brightness) + ":contrast=" + decimal(contrast)
                        + ":saturation=" + decimal(saturation));
            }
            double temperature = settings.temperature() == null ? 0 : settings.temperature();
            if (temperature != 0) filters.add("colorbalance=rs=" + decimal(temperature * 0.12)
                    + ":bs=" + decimal(temperature * -0.12));
            if (Boolean.TRUE.equals(settings.useLut()) && lutPath != null) {
                filters.add("lut3d=file='" + filterPath(lutPath) + "'");
            }
        }
        filters.add("setsar=1");
        filters.add("format=yuv420p");
        return String.join(",", filters);
    }

    private String overlayPosition(RenderAssetResolver.RenderAsset asset) {
        String base = switch (String.valueOf(asset.position())) {
            case "TOP_LEFT" -> "40:40"; case "TOP_RIGHT" -> "W-w-40:40";
            case "BOTTOM_LEFT" -> "40:H-h-40"; case "BOTTOM_RIGHT" -> "W-w-40:H-h-40";
            default -> "(W-w)/2:(H-h)/2";
        };
        String[] coordinate = base.split(":", 2);
        double start = Math.max(0, asset.startOffsetSeconds());
        return switch (String.valueOf(asset.animation())) {
            case "SLIDE" -> "if(lt(t," + decimal(start + .28) + "),-w+(" + coordinate[0]
                    + "+w)*(t-" + decimal(start) + ")/.28," + coordinate[0] + "):" + coordinate[1];
            case "BOUNCE" -> coordinate[0] + ":" + coordinate[1] + "+18*abs(sin(8*(t-" + decimal(start) + ")))";
            default -> base;
        };
    }

    private String animationFilter(RenderAssetResolver.RenderAsset asset) {
        if (!List.of("FADE", "POP").contains(String.valueOf(asset.animation()))) return "";
        double start = Math.max(0, asset.startOffsetSeconds());
        StringBuilder value = new StringBuilder(",fade=t=in:st=").append(decimal(start)).append(":d=0.200:alpha=1");
        if (asset.endOffsetSeconds() != null && asset.endOffsetSeconds() - start > .25) {
            value.append(",fade=t=out:st=").append(decimal(asset.endOffsetSeconds() - .2)).append(":d=0.200:alpha=1");
        }
        return value.toString();
    }

    private String enable(RenderAssetResolver.RenderAsset asset, TimelineSegment segment) {
        double start = Math.max(0, asset.startOffsetSeconds());
        double duration = Math.max(.01, segment.sourceEndSeconds() - segment.sourceStartSeconds());
        double safeStart = Math.min(start, duration);
        double end = asset.endOffsetSeconds() == null ? duration : Math.min(duration, asset.endOffsetSeconds());
        return "between(t," + decimal(safeStart) + "," + decimal(Math.max(safeStart, end)) + ")";
    }

    private int even(double value) {
        int rounded = (int) Math.round(value);
        return rounded % 2 == 0 ? rounded : rounded + 1;
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private String filterPath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/').replace(":", "\\:").replace("'", "\\'");
    }
}
