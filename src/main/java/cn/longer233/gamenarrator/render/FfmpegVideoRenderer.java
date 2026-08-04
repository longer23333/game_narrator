package cn.longer233.gamenarrator.render;

import cn.longer233.gamenarrator.audio.ProceduralSoundEffectLibrary;
import cn.longer233.gamenarrator.audio.SoundCue;
import cn.longer233.gamenarrator.effect.EffectPlan;
import cn.longer233.gamenarrator.effect.EffectPreset;
import cn.longer233.gamenarrator.effect.EffectSettingsRequest;
import cn.longer233.gamenarrator.effect.SemanticEffectPlanner;
import cn.longer233.gamenarrator.effect.TransitionType;
import cn.longer233.gamenarrator.effect.VisualEffectType;
import cn.longer233.gamenarrator.timeline.TimelineSegment;
import cn.longer233.gamenarrator.subtitle.AssSubtitleBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class FfmpegVideoRenderer {
    private static final Logger log = LoggerFactory.getLogger(FfmpegVideoRenderer.class);
    private final ObjectMapper objectMapper;
    private final String ffmpegCommand;
    private final String preferredEncoder;
    private final SemanticEffectPlanner effectPlanner;
    private final AssSubtitleBuilder assSubtitleBuilder;
    private final ProceduralSoundEffectLibrary soundEffectLibrary;
    private final RenderAssetResolver renderAssetResolver;

    public FfmpegVideoRenderer(ObjectMapper objectMapper,
            SemanticEffectPlanner effectPlanner,
            AssSubtitleBuilder assSubtitleBuilder,
            ProceduralSoundEffectLibrary soundEffectLibrary,
            RenderAssetResolver renderAssetResolver,
            @Value("${game-narrator.ffmpeg-command}") String ffmpegCommand,
            @Value("${game-narrator.render.video-encoder:h264_nvenc}") String preferredEncoder) {
        this.objectMapper = objectMapper;
        this.effectPlanner = effectPlanner;
        this.assSubtitleBuilder = assSubtitleBuilder;
        this.soundEffectLibrary = soundEffectLibrary;
        this.renderAssetResolver = renderAssetResolver;
        this.ffmpegCommand = ffmpegCommand;
        this.preferredEncoder = preferredEncoder;
    }

    public RenderResult render(Path sourceVideo, Path timelinePath, boolean hasSourceAudio) {
        return render(sourceVideo, timelinePath, hasSourceAudio, null, null);
    }

    public RenderResult render(Path sourceVideo, Path timelinePath, boolean hasSourceAudio,
                               EffectPreset preset) {
        return render(sourceVideo, timelinePath, hasSourceAudio, preset, null);
    }

    public RenderResult render(Path sourceVideo, Path timelinePath, boolean hasSourceAudio,
                               EffectPreset preset, EffectSettingsRequest settings) {
        return render(sourceVideo, timelinePath, hasSourceAudio, preset, settings, ignored -> { });
    }

    public RenderResult render(Path sourceVideo, Path timelinePath, boolean hasSourceAudio,
                               EffectPreset preset, EffectSettingsRequest settings,
                               java.util.function.IntConsumer progressConsumer) {
        Path workDirectory = timelinePath.getParent().resolve("render-work");
        try {
            var root = objectMapper.readTree(timelinePath.toFile());
            List<TimelineSegment> segments = objectMapper.readerForListOf(TimelineSegment.class)
                    .readValue(root.path("segments"));
            if (segments.isEmpty()) throw new IllegalStateException("剪辑时间线为空");
            Path taskDirectory = timelinePath.getParent();
            Files.createDirectories(workDirectory);
            List<RenderAssetResolver.RenderAsset> storyboardAssets = renderAssetResolver.resolve(timelinePath);
            log.info("RENDERING_BEGIN segments={} source={} preferredEncoder={}",
                    segments.size(), sourceVideo, preferredEncoder);

            List<Path> clips = new ArrayList<>();
            List<java.util.Map<String, Object>> effectManifest = new ArrayList<>();
            List<EffectPlan> effectPlans = new ArrayList<>();
            String encoder = preferredEncoder;
            for (int index = 0; index < segments.size(); index++) {
                int clipPosition = index;
                Path clip = workDirectory.resolve("clip-%02d.mp4".formatted(index + 1));
                TimelineSegment segment = segments.get(index);
                EffectPlan effectPlan = effectPlanner.plan(
                        segment.effectCue(), segment.narration(), segment.sequence(), preset);
                effectPlans.add(effectPlan);
                try {
                    encodeClip(sourceVideo, segment, clip, hasSourceAudio, encoder, effectPlan, preset,
                            visualAssets(storyboardAssets, segment.sequence()), fraction -> progressConsumer.accept(
                                    10 + (int) Math.floor((clipPosition + fraction) / segments.size() * 65)));
                } catch (IllegalStateException exception) {
                    if (index == 0 && !"libx264".equals(encoder)) {
                        log.warn("RENDER_ENCODER_FALLBACK from={} to=libx264 reason={}", encoder, exception.getMessage());
                        encoder = "libx264";
                        encodeClip(sourceVideo, segment, clip, hasSourceAudio, encoder, effectPlan, preset,
                                visualAssets(storyboardAssets, segment.sequence()), fraction -> progressConsumer.accept(
                                        10 + (int) Math.floor((clipPosition + fraction) / segments.size() * 65)));
                    } else {
                        throw exception;
                    }
                }
                clips.add(clip);
                effectManifest.add(java.util.Map.of(
                        "sequence", segment.sequence(),
                        "effectCue", segment.effectCue() == null ? "" : segment.effectCue(),
                        "effects", effectPlan.effects(),
                        "transition", effectPlan.transition(),
                        "reason", effectPlan.reason()));
                log.info("RENDER_CLIP_SUCCESS sequence={} encoder={} output={}", index + 1, encoder, clip);
            }
            cn.longer233.gamenarrator.common.AtomicArtifactWriter.writeJson(objectMapper,
                    taskDirectory.resolve("effects-manifest.json"),
                    java.util.Map.of(
                            "version", 2,
                            "presetCode", preset == null ? "AUTO" : preset.code(),
                            "intensity", preset == null ? 0.75 : preset.defaultIntensity(),
                            "subtitleTheme", preset == null ? "DEFAULT" : preset.subtitleTheme(),
                            "sourceAudioVolume", preset == null ? 0.20 : preset.sourceAudioVolume(),
                            "segments", effectManifest));

            Path concatList = workDirectory.resolve("concat.txt");
            cn.longer233.gamenarrator.common.AtomicArtifactWriter.writeText(concatList, clips.stream()
                    .map(path -> "file '" + path.toAbsolutePath().toString().replace('\\', '/') + "'")
                    .reduce((left, right) -> left + System.lineSeparator() + right).orElseThrow(),
                    StandardCharsets.UTF_8);
            Path baseVideo = workDirectory.resolve("base.mp4");
            run(List.of(ffmpegCommand, "-y", "-hide_banner", "-loglevel", "warning",
                    "-f", "concat", "-safe", "0", "-i", concatList.toString(),
                    "-c", "copy", baseVideo.toString()), Duration.ofMinutes(30), "片段拼接");

            Path subtitle = taskDirectory.resolve("generated-subtitles.srt");
            progressConsumer.accept(80);
            cn.longer233.gamenarrator.common.AtomicArtifactWriter.writeText(
                    subtitle, buildSrt(segments), StandardCharsets.UTF_8);
            boolean dynamicSubtitles = settings != null && Boolean.TRUE.equals(settings.dynamicSubtitles());
            boolean soundEffects = settings != null && Boolean.TRUE.equals(settings.soundEffects());
            List<SoundCue> soundCues = soundEffects
                    ? soundEffectLibrary.create(taskDirectory, segments, effectPlans) : List.of();
            if (soundEffects) {
                cn.longer233.gamenarrator.common.AtomicArtifactWriter.writeJson(objectMapper,
                        taskDirectory.resolve("sound-effects-manifest.json"),
                        java.util.Map.of("version", 1, "cues", soundCues));
            }
            Path dynamicSubtitle = null;
            if (dynamicSubtitles) {
                dynamicSubtitle = taskDirectory.resolve("generated-subtitles.ass");
                cn.longer233.gamenarrator.common.AtomicArtifactWriter.writeText(dynamicSubtitle,
                        assSubtitleBuilder.build(segments,
                                preset == null ? "ANIME_OUTLINE" : preset.subtitleTheme()),
                        StandardCharsets.UTF_8);
            }
            Path output = taskDirectory.resolve("final-video.mp4");
            mixVoiceAndSubtitle(baseVideo, segments, subtitle, output,
                    preset == null ? 0.20 : preset.sourceAudioVolume(), dynamicSubtitle, soundCues,
                    storyboardAssets.stream().filter(RenderAssetResolver.RenderAsset::audio).toList());
            long size = Files.size(output);
            progressConsumer.accept(95);
            log.info("RENDERING_SUCCESS encoder={} duration={} sizeBytes={} output={}",
                    encoder, root.path("outputDurationSeconds").asDouble(), size, output);
            return new RenderResult(output.toString(), subtitle.toString(), size);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("视频渲染失败：" + exception.getMessage(), exception);
        } finally {
            cleanupWorkDirectory(workDirectory);
        }
    }

    private void encodeClip(Path source, TimelineSegment segment, Path output,
                            boolean hasAudio, String encoder, EffectPlan effectPlan, EffectPreset preset,
                            List<RenderAssetResolver.RenderAsset> assets,
                            java.util.function.DoubleConsumer progressConsumer) {
        List<String> command = new ArrayList<>(List.of(ffmpegCommand, "-y", "-hide_banner",
                "-loglevel", "warning", "-ss", decimal(segment.sourceStartSeconds()),
                "-t", decimal(segment.sourceEndSeconds() - segment.sourceStartSeconds()),
                "-i", source.toString()));
        if (!hasAudio) {
            command.addAll(List.of("-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo"));
        }
        double duration = segment.sourceEndSeconds() - segment.sourceStartSeconds();
        for (RenderAssetResolver.RenderAsset asset : assets) {
            if ("MEME".equals(asset.assetType())) command.addAll(List.of("-loop", "1", "-t", decimal(duration)));
            else command.addAll(List.of("-stream_loop", "-1"));
            command.addAll(List.of("-i", asset.path().toString()));
        }
        command.addAll(List.of("-map", assets.isEmpty() ? "0:v:0" : "[vout]",
                "-map", hasAudio ? "0:a:0" : "1:a:0"));
        if (assets.isEmpty()) command.addAll(List.of("-vf", buildVideoFilter(segment, effectPlan, preset)));
        else command.addAll(List.of("-filter_complex", buildStoryboardVideoFilter(segment, effectPlan, preset,
                assets, hasAudio ? 1 : 2)));
        command.addAll(List.of("-c:v", encoder));
        if ("h264_nvenc".equals(encoder)) command.addAll(List.of("-preset", "p4", "-cq", "24"));
        else command.addAll(List.of("-preset", "veryfast", "-crf", "23"));
        command.addAll(List.of("-c:a", "aac", "-ar", "48000", "-ac", "2", "-shortest",
                "-progress", "pipe:1", "-nostats", output.toString()));
        run(command, Duration.ofMinutes(45), "片段编码", line -> {
            if (!line.startsWith("out_time_us=")) return;
            try {
                double elapsed = Long.parseLong(line.substring("out_time_us=".length()).trim()) / 1_000_000d;
                progressConsumer.accept(Math.max(0, Math.min(1, elapsed / Math.max(.1, duration))));
            } catch (NumberFormatException ignored) { }
        });
    }

    private List<RenderAssetResolver.RenderAsset> visualAssets(List<RenderAssetResolver.RenderAsset> assets,
                                                                int sequence) {
        return assets.stream().filter(asset -> !asset.audio() && asset.clipIndex() == sequence).limit(2).toList();
    }

    String buildStoryboardVideoFilter(TimelineSegment segment, EffectPlan plan, EffectPreset preset,
                                      List<RenderAssetResolver.RenderAsset> assets, int firstInput) {
        StringBuilder graph = new StringBuilder("[0:v]").append(buildVideoFilter(segment, plan, preset)).append("[base];");
        String previous = "base";
        for (int index = 0; index < assets.size(); index++) {
            RenderAssetResolver.RenderAsset asset = assets.get(index);
            String prepared = "asset" + index;
            boolean background = "BACKGROUND".equals(asset.placementType());
            graph.append('[').append(firstInput + index).append(":v]")
                    .append("setpts=PTS-STARTPTS,");
            if (background) graph.append("scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080,")
                    .append("format=rgba,colorchannelmixer=aa=0.38");
            else graph.append("scale=720:720:force_original_aspect_ratio=decrease,format=rgba");
            if (asset.cutoutApplied()) graph.append(",chromakey=0x00FF00:0.18:0.08");
            graph.append('[').append(prepared).append("];[").append(previous).append("][")
                    .append(prepared).append("]overlay=").append(overlayPosition(asset.position()))
                    .append(":shortest=1[v").append(index).append("];");
            previous = "v" + index;
        }
        graph.append('[').append(previous).append("]null[vout]");
        return graph.toString();
    }

    private String overlayPosition(String position) {
        return switch (String.valueOf(position)) {
            case "TOP_LEFT" -> "40:40"; case "TOP_RIGHT" -> "W-w-40:40";
            case "BOTTOM_LEFT" -> "40:H-h-40"; case "BOTTOM_RIGHT" -> "W-w-40:H-h-40";
            default -> "(W-w)/2:(H-h)/2";
        };
    }

    String buildVideoFilter(TimelineSegment segment, EffectPlan plan) {
        return buildVideoFilter(segment, plan, null);
    }

    String buildVideoFilter(TimelineSegment segment, EffectPlan plan, EffectPreset preset) {
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
        if (plan.effects().contains(VisualEffectType.WHITE_FLASH)) {
            filters.add("fade=t=in:st=0:d=" + decimal(0.08 + 0.08 * intensity) + ":color=white");
        }
        if (plan.effects().contains(VisualEffectType.SLOW_MOTION)) {
            filters.add("tmix=frames=3:weights='1 1 1'");
        }
        if (plan.effects().contains(VisualEffectType.FREEZE_ACCENT)) {
            filters.add("eq=saturation=0.75:contrast=1.18");
            filters.add("unsharp=5:5:1.2");
        }
        if (plan.effects().contains(VisualEffectType.SPEED_LINES)) {
            filters.add("vignette=PI/5");
            filters.add("unsharp=7:7:1.5");
        }
        if (plan.effects().contains(VisualEffectType.CINEMA_BARS)) {
            filters.add("drawbox=x=0:y=0:w=iw:h=70:color=black:t=fill");
            filters.add("drawbox=x=0:y=ih-70:w=iw:h=70:color=black:t=fill");
        }
        if (plan.effects().contains(VisualEffectType.TITLE_CARD)) {
            filters.add("drawbox=x=0:y=0:w=iw:h=ih:color=black@0.22:t=fill:enable='between(t,0,0.8)'");
        }
        if (plan.effects().contains(VisualEffectType.GAUSSIAN_BLUR)) {
            filters.add("gblur=sigma=" + decimal(0.5 + 1.8 * intensity));
        }
        if (plan.effects().contains(VisualEffectType.VIGNETTE)) {
            filters.add("vignette=angle='PI/2.8'");
        }
        if (plan.effects().contains(VisualEffectType.BLACK_AND_WHITE)) {
            filters.add("hue=s=0");
        }
        if (plan.effects().contains(VisualEffectType.WARM_TONE)) {
            filters.add("colorbalance=rs=" + decimal(0.08 * intensity) + ":bs=-" + decimal(0.06 * intensity));
        }
        if (plan.effects().contains(VisualEffectType.COOL_TONE)) {
            filters.add("colorbalance=rs=-" + decimal(0.05 * intensity) + ":bs=" + decimal(0.08 * intensity));
        }
        if (plan.effects().contains(VisualEffectType.HIGH_CONTRAST)) {
            filters.add("eq=contrast=" + decimal(1 + 0.28 * intensity) + ":saturation=" + decimal(1 + 0.10 * intensity));
        }
        if (plan.effects().contains(VisualEffectType.RGB_SPLIT)) {
            filters.add("rgbashift=rh=" + Math.max(1,Math.round(5 * intensity)) + ":bh=-" + Math.max(1,Math.round(4 * intensity)));
        }
        if (plan.effects().contains(VisualEffectType.HORIZONTAL_FLIP)) {
            filters.add("hflip");
        }
        if (plan.effects().contains(VisualEffectType.PIXELATE)) {
            int width=even(1920-(1500*intensity)); int height=even(1080-(840*intensity));
            filters.add("scale=" + Math.max(320,width) + ":" + Math.max(180,height) + ":flags=neighbor");
            filters.add("scale=1920:1080:flags=neighbor");
        }
        if (plan.effects().contains(VisualEffectType.LENS_DISTORTION)) {
            filters.add("lenscorrection=k1=" + decimal(-0.12 * intensity) + ":k2=" + decimal(0.04 * intensity));
        }
        if (plan.transition() == TransitionType.FADE || plan.transition() == TransitionType.DISSOLVE) {
            filters.add("fade=t=in:st=0:d=" + decimal(transitionDuration));
            filters.add("fade=t=out:st=" + decimal(Math.max(0, duration - transitionDuration))
                    + ":d=" + decimal(transitionDuration));
        } else if (plan.transition() == TransitionType.PUSH) {
            filters.add("crop=iw:ih:x='min(30,30*t/0.25)':y=0");
            filters.add("scale=1920:1080");
        }
        filters.add("setsar=1");
        filters.add("format=yuv420p");
        return String.join(",", filters);
    }

    private int even(double value) {
        int rounded = (int) Math.round(value);
        return rounded % 2 == 0 ? rounded : rounded + 1;
    }

    private void mixVoiceAndSubtitle(Path baseVideo, List<TimelineSegment> segments,
                                     Path subtitle, Path output, double sourceAudioVolume,
                                     Path dynamicSubtitle, List<SoundCue> soundCues,
                                     List<RenderAssetResolver.RenderAsset> externalAudio) {
        List<String> command = new ArrayList<>(List.of(ffmpegCommand, "-y", "-hide_banner",
                "-loglevel", "warning", "-i", baseVideo.toString()));
        for (TimelineSegment segment : segments) command.addAll(List.of("-i", segment.voicePath()));
        for (SoundCue cue : soundCues) command.addAll(List.of("-i", cue.audioPath()));
        for (RenderAssetResolver.RenderAsset asset : externalAudio) {
            if ("BACKGROUND_AUDIO".equals(asset.placementType())) command.addAll(List.of("-stream_loop", "-1"));
            command.addAll(List.of("-i", asset.path().toString()));
        }
        command.addAll(List.of("-i", subtitle.toString()));
        StringBuilder filter = new StringBuilder("[0:a]volume=")
                .append(decimal(sourceAudioVolume)).append("[bg];");
        for (int index = 0; index < segments.size(); index++) {
            TimelineSegment segment = segments.get(index);
            long delay = Math.round(segment.outputStartSeconds() * 1000);
            filter.append('[').append(index + 1).append(":a]");
            double clipDuration = segment.outputEndSeconds() - segment.outputStartSeconds();
            if (segment.voiceDurationSeconds() > clipDuration - 0.25) {
                double speed = Math.min(2.0,
                        segment.voiceDurationSeconds() / Math.max(0.5, clipDuration - 0.25));
                filter.append("atempo=").append(decimal(speed)).append(',');
            }
            filter.append("adelay=")
                    .append(delay).append('|').append(delay).append("[v").append(index).append("];");
        }
        for (int index = 0; index < soundCues.size(); index++) {
            SoundCue cue = soundCues.get(index);
            int inputIndex = segments.size() + 1 + index;
            long delay = Math.round(cue.startSeconds() * 1000);
            filter.append('[').append(inputIndex).append(":a]volume=")
                    .append(decimal(cue.volume())).append(",adelay=")
                    .append(delay).append('|').append(delay).append("[s").append(index).append("];");
        }
        double totalDuration = segments.getLast().outputEndSeconds();
        for (int index = 0; index < externalAudio.size(); index++) {
            RenderAssetResolver.RenderAsset asset = externalAudio.get(index);
            int inputIndex = segments.size() + 1 + soundCues.size() + index;
            TimelineSegment segment = segments.stream().filter(item -> item.sequence() == asset.clipIndex())
                    .findFirst().orElse(segments.getFirst());
            filter.append('[').append(inputIndex).append(":a]");
            if ("BACKGROUND_AUDIO".equals(asset.placementType())) {
                filter.append("atrim=0:").append(decimal(totalDuration)).append(",volume=0.14");
            } else {
                long delay = Math.round(segment.outputStartSeconds() * 1000);
                filter.append("atrim=0:").append(decimal(segment.outputEndSeconds()-segment.outputStartSeconds()))
                        .append(",volume=0.48,adelay=").append(delay).append('|').append(delay);
            }
            filter.append("[x").append(index).append("];");
        }
        for (int index = 0; index < segments.size(); index++) filter.append("[v").append(index).append(']');
        for (int index = 0; index < soundCues.size(); index++) filter.append("[s").append(index).append(']');
        for (int index = 0; index < externalAudio.size(); index++) filter.append("[x").append(index).append(']');
        filter.append("amix=inputs=").append(segments.size() + soundCues.size() + externalAudio.size())
                .append(":duration=longest:normalize=0,asplit=2[voiceSide][voiceMix];")
                .append("[bg][voiceSide]sidechaincompress=threshold=0.02:ratio=8:attack=20:release=320[ducked];")
                .append("[ducked][voiceMix]amix=inputs=2:duration=first:dropout_transition=0[aout]");
        int subtitleInput = segments.size() + soundCues.size() + externalAudio.size() + 1;
        command.addAll(List.of("-filter_complex", filter.toString()));
        if (dynamicSubtitle != null) {
            command.addAll(List.of("-vf", "ass='" + filterPath(dynamicSubtitle) + "'",
                    "-map", "0:v:0", "-map", "[aout]", "-c:v", preferredEncoder));
            if ("h264_nvenc".equals(preferredEncoder)) {
                command.addAll(List.of("-preset", "p4", "-cq", "22"));
            }
        } else {
            command.addAll(List.of("-map", "0:v:0", "-map", "[aout]",
                    "-map", subtitleInput + ":s:0", "-c:v", "copy",
                    "-c:s", "mov_text", "-metadata:s:s:0", "language=zho"));
        }
        command.addAll(List.of("-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart",
                output.toString()));
        run(command, Duration.ofMinutes(45), "音画合成");
    }

    private String filterPath(Path path) {
        return path.toAbsolutePath().toString().replace("\\", "/")
                .replace(":", "\\:").replace("'", "\\'");
    }

    private String buildSrt(List<TimelineSegment> segments) {
        StringBuilder result = new StringBuilder();
        for (TimelineSegment segment : segments) {
            String text = segment.subtitle() == null || segment.subtitle().isBlank()
                    ? segment.narration() : segment.subtitle();
            if (text == null || text.isBlank()) text = "\u200B";
            result.append(segment.sequence()).append('\n')
                    .append(srtTime(segment.outputStartSeconds())).append(" --> ")
                    .append(srtTime(segment.outputEndSeconds())).append('\n')
                    .append(text.replace("\r", " ").replace("\n", " ")).append("\n\n");
        }
        return result.toString();
    }

    private String srtTime(double seconds) {
        long millis = Math.round(seconds * 1000);
        return "%02d:%02d:%02d,%03d".formatted(millis / 3_600_000,
                millis / 60_000 % 60, millis / 1000 % 60, millis % 1000);
    }

    private String decimal(double value) { return String.format(Locale.ROOT, "%.3f", value); }

    private void cleanupWorkDirectory(Path workDirectory) {
        Path normalized = workDirectory.toAbsolutePath().normalize();
        if (!"render-work".equals(String.valueOf(normalized.getFileName())) || !Files.isDirectory(normalized)) return;
        try (var paths = Files.walk(normalized)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (java.io.IOException exception) {
                    log.warn("RENDER_WORK_CLEANUP_FAILED path={} message={}", path, exception.getMessage());
                }
            });
        } catch (java.io.IOException exception) {
            log.warn("RENDER_WORK_CLEANUP_FAILED path={} message={}", normalized, exception.getMessage());
        }
    }

    private void run(List<String> command, Duration timeout, String operation) {
        run(command, timeout, operation, ignored -> { });
    }

    private void run(List<String> command, Duration timeout, String operation,
                     java.util.function.Consumer<String> outputLine) {
        try {
            var result = cn.longer233.gamenarrator.common.ExternalProcessRunner.run(
                    command, timeout, null, outputLine);
            if (result.exitCode() != 0) {
                throw new IllegalStateException(operation + "失败，FFmpeg 退出码 " + result.exitCode()
                        + "：" + tail(result.output(), 1600));
            }
        } catch (cn.longer233.gamenarrator.common.ExternalProcessRunner.ProcessTimeoutException exception) {
            throw new IllegalStateException(operation + "超时", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(operation + "被中断", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(operation + "无法启动 FFmpeg：" + exception.getMessage(), exception);
        }
    }

    private String tail(String value, int limit) {
        return value.length() <= limit ? value : value.substring(value.length() - limit);
    }
}
