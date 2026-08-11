package cn.longer233.gamenarrator.render;

import cn.longer233.gamenarrator.audio.ProceduralSoundEffectLibrary;
import cn.longer233.gamenarrator.audio.SoundCue;
import cn.longer233.gamenarrator.effect.EffectPlan;
import cn.longer233.gamenarrator.effect.EffectPreset;
import cn.longer233.gamenarrator.effect.EffectSettingsRequest;
import cn.longer233.gamenarrator.effect.SemanticEffectPlanner;
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
    private final FfmpegEncoderCapabilities encoderCapabilities;
    private final SemanticEffectPlanner effectPlanner;
    private final AssSubtitleBuilder assSubtitleBuilder;
    private final ProceduralSoundEffectLibrary soundEffectLibrary;
    private final RenderAssetResolver renderAssetResolver;
    private final RenderVideoFilterBuilder videoFilterBuilder;
    private final RenderAudioMixBuilder audioMixBuilder;

    public FfmpegVideoRenderer(ObjectMapper objectMapper,
            SemanticEffectPlanner effectPlanner,
            AssSubtitleBuilder assSubtitleBuilder,
            ProceduralSoundEffectLibrary soundEffectLibrary,
            RenderAssetResolver renderAssetResolver,
            RenderVideoFilterBuilder videoFilterBuilder,
            RenderAudioMixBuilder audioMixBuilder,
            FfmpegEncoderCapabilities encoderCapabilities,
            @Value("${game-narrator.ffmpeg-command}") String ffmpegCommand,
            @Value("${game-narrator.render.video-encoder:h264_nvenc}") String preferredEncoder) {
        this.objectMapper = objectMapper;
        this.effectPlanner = effectPlanner;
        this.assSubtitleBuilder = assSubtitleBuilder;
        this.soundEffectLibrary = soundEffectLibrary;
        this.renderAssetResolver = renderAssetResolver;
        this.videoFilterBuilder = videoFilterBuilder;
        this.audioMixBuilder = audioMixBuilder;
        this.encoderCapabilities = encoderCapabilities;
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
            Path previewDirectory = taskDirectory.resolve("render-preview");
            preparePreviewDirectory(previewDirectory);
            List<java.util.Map<String, Object>> previewFrames = new ArrayList<>();
            List<RenderAssetResolver.RenderAsset> storyboardAssets = renderAssetResolver.resolve(timelinePath);
            log.info("RENDERING_BEGIN segments={} source={} preferredEncoder={}",
                    segments.size(), sourceVideo, preferredEncoder);

            List<Path> clips = new ArrayList<>();
            List<java.util.Map<String, Object>> effectManifest = new ArrayList<>();
            List<EffectPlan> effectPlans = new ArrayList<>();
            String encoder = encoderCapabilities.resolve(preferredEncoder, "libx264");
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
                createPreviewFrame(clip, previewDirectory, segment, index, previewFrames);
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
                    storyboardAssets.stream().filter(RenderAssetResolver.RenderAsset::audio).toList(), encoder);
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
        if (assets.isEmpty()) command.addAll(List.of("-vf", videoFilterBuilder.video(segment, effectPlan, preset)));
        else command.addAll(List.of("-filter_complex", videoFilterBuilder.storyboard(segment, effectPlan, preset,
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

    private void preparePreviewDirectory(Path previewDirectory) throws java.io.IOException {
        Files.createDirectories(previewDirectory);
        try (var files = Files.list(previewDirectory)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                String name = path.getFileName().toString();
                if (name.equals("manifest.json") || name.matches("frame-\\d{3}\\.jpg")) {
                    try { Files.deleteIfExists(path); }
                    catch (java.io.IOException exception) {
                        log.warn("RENDER_PREVIEW_CLEANUP_FAILED path={} message={}", path, exception.getMessage());
                    }
                }
            });
        }
    }

    private void createPreviewFrame(Path clip, Path previewDirectory, TimelineSegment segment, int index,
                                    List<java.util.Map<String, Object>> previewFrames) {
        Path output = previewDirectory.resolve("frame-%03d.jpg".formatted(index + 1));
        try {
            run(List.of(ffmpegCommand, "-y", "-hide_banner", "-loglevel", "error", "-ss", "0.200",
                    "-i", clip.toString(), "-frames:v", "1", "-vf", "scale=320:-2", "-q:v", "4",
                    output.toString()), Duration.ofMinutes(2), "渲染预览帧生成");
            previewFrames.add(java.util.Map.of(
                    "index", index + 1,
                    "sequence", segment.sequence(),
                    "outputStartSeconds", segment.outputStartSeconds(),
                    "outputEndSeconds", segment.outputEndSeconds(),
                    "fileName", output.getFileName().toString()));
            cn.longer233.gamenarrator.common.AtomicArtifactWriter.writeJson(objectMapper,
                    previewDirectory.resolve("manifest.json"),
                    java.util.Map.of("version", 1, "frames", List.copyOf(previewFrames)));
        } catch (Exception exception) {
            log.warn("RENDER_PREVIEW_FRAME_FAILED sequence={} message={}", segment.sequence(), exception.getMessage());
        }
    }

    private void mixVoiceAndSubtitle(Path baseVideo, List<TimelineSegment> segments,
                                     Path subtitle, Path output, double sourceAudioVolume,
                                     Path dynamicSubtitle, List<SoundCue> soundCues,
                                     List<RenderAssetResolver.RenderAsset> externalAudio,
                                     String encoder) {
        try {
            mixVoiceAndSubtitleOnce(baseVideo, segments, subtitle, output, sourceAudioVolume,
                    dynamicSubtitle, soundCues, externalAudio, encoder);
        } catch (IllegalStateException exception) {
            if (dynamicSubtitle == null || "libx264".equals(encoder)) throw exception;
            log.warn("RENDER_MIX_ENCODER_FALLBACK from={} to=libx264 reason={}", encoder, exception.getMessage());
            try {
                Files.deleteIfExists(output);
            } catch (java.io.IOException cleanupException) {
                log.warn("RENDER_MIX_PARTIAL_OUTPUT_CLEANUP_FAILED output={} message={}",
                        output, cleanupException.getMessage());
            }
            mixVoiceAndSubtitleOnce(baseVideo, segments, subtitle, output, sourceAudioVolume,
                    dynamicSubtitle, soundCues, externalAudio, "libx264");
        }
    }

    private void mixVoiceAndSubtitleOnce(Path baseVideo, List<TimelineSegment> segments,
                                         Path subtitle, Path output, double sourceAudioVolume,
                                         Path dynamicSubtitle, List<SoundCue> soundCues,
                                         List<RenderAssetResolver.RenderAsset> externalAudio,
                                         String encoder) {
        List<String> command = new ArrayList<>(List.of(ffmpegCommand, "-y", "-hide_banner",
                "-loglevel", "warning", "-i", baseVideo.toString()));
        for (TimelineSegment segment : segments) command.addAll(List.of("-i", segment.voicePath()));
        for (SoundCue cue : soundCues) command.addAll(List.of("-i", cue.audioPath()));
        for (RenderAssetResolver.RenderAsset asset : externalAudio) {
            if ("BACKGROUND_AUDIO".equals(asset.placementType())) command.addAll(List.of("-stream_loop", "-1"));
            command.addAll(List.of("-i", asset.path().toString()));
        }
        command.addAll(List.of("-i", subtitle.toString()));
        RenderAudioMixBuilder.AudioMixPlan mixPlan = audioMixBuilder.build(
                segments, soundCues, externalAudio, sourceAudioVolume);
        command.addAll(List.of("-filter_complex", mixPlan.filterGraph()));
        if (dynamicSubtitle != null) {
            command.addAll(List.of("-vf", "ass='" + filterPath(dynamicSubtitle) + "'",
                    "-map", "0:v:0", "-map", "[aout]", "-c:v", encoder));
            if ("h264_nvenc".equals(encoder)) {
                command.addAll(List.of("-preset", "p4", "-cq", "22"));
            } else if ("libx264".equals(encoder)) {
                command.addAll(List.of("-preset", "veryfast", "-crf", "21"));
            }
        } else {
            command.addAll(List.of("-map", "0:v:0", "-map", "[aout]",
                    "-map", mixPlan.subtitleInput() + ":s:0", "-c:v", "copy",
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
