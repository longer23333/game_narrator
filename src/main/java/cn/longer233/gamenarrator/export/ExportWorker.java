package cn.longer233.gamenarrator.export;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import cn.longer233.gamenarrator.media.FfmpegMediaProbe;
import cn.longer233.gamenarrator.render.FfmpegEncoderCapabilities;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class ExportWorker {
    private static final Logger log = LoggerFactory.getLogger(ExportWorker.class);
    private final JdbcTemplate jdbc;
    private final String ffmpegCommand;
    private final FfmpegMediaProbe mediaProbe;
    private final FfmpegEncoderCapabilities encoderCapabilities;

    public ExportWorker(JdbcTemplate jdbc,
                        @Value("${game-narrator.ffmpeg-command}") String ffmpegCommand,
                        FfmpegMediaProbe mediaProbe,
                        FfmpegEncoderCapabilities encoderCapabilities) {
        this.jdbc = jdbc;
        this.ffmpegCommand = ffmpegCommand;
        this.mediaProbe = mediaProbe;
        this.encoderCapabilities = encoderCapabilities;
    }

    @Async
    public void execute(UUID jobId, Path sourceVideo, Path subtitle, Path voiceManifest,
                        Path output, ExportPresetView preset, CreateExportRequest request) {
        try {
            update(jobId, "RUNNING", 10, null);
            Files.createDirectories(output.getParent());
            String container = preset.container().toUpperCase();
            if ("SRT".equals(container)) {
                if (subtitle == null || !Files.isRegularFile(subtitle)) {
                    throw new IllegalStateException("当前任务没有可导出的字幕文件");
                }
                Files.copy(subtitle, output);
            } else {
                double durationSeconds = mediaProbe.inspect(sourceVideo).durationSeconds();
                if ("WAV".equals(container)) {
                    exportAudio(jobId, sourceVideo, output, request, preset, durationSeconds);
                } else {
                    exportVideo(jobId, sourceVideo, subtitle, output, preset, request, durationSeconds);
                }
            }
            update(jobId, "RUNNING", 90, null);
            registerArtifact(jobId, output);
            jdbc.update("""
                    UPDATE export_job SET status='COMPLETED', progress=100,
                    completed_at=?, expires_at=? WHERE id=?
                    """, OffsetDateTime.now(), OffsetDateTime.now().plusDays(7), jobId);
            log.info("EXPORT_SUCCESS jobId={} preset={} output={} sizeBytes={}",
                    jobId, preset.name(), output, Files.size(output));
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            update(jobId, "FAILED", 0, message.length() > 1900 ? message.substring(0, 1900) : message);
            log.error("EXPORT_FAILED jobId={} output={}", jobId, output, exception);
        }
    }

    private void exportAudio(UUID jobId, Path source, Path output, CreateExportRequest request,
                             ExportPresetView preset, double durationSeconds) {
        run(jobId, List.of(ffmpegCommand, "-y", "-hide_banner", "-loglevel", "warning",
                "-i", source.toString(), "-vn", "-c:a", "pcm_s16le",
                "-ar", String.valueOf(preset.audioSampleRate()), "-ac", "2",
                "-progress", "pipe:1", "-nostats", output.toString()), durationSeconds);
    }

    private void exportVideo(UUID jobId, Path source, Path subtitle, Path output,
                             ExportPresetView preset, CreateExportRequest request, double durationSeconds) {
        List<String> command = new ArrayList<>(List.of(ffmpegCommand, "-y", "-hide_banner",
                "-loglevel", "warning", "-i", source.toString()));
        Integer width = request.width() != null ? request.width() : preset.width();
        Integer height = request.height() != null ? request.height() : preset.height();
        Double frameRate = request.frameRate() != null ? request.frameRate() : preset.frameRate();
        String subtitleMode = request.subtitleMode() == null ? preset.subtitleMode() : request.subtitleMode();
        List<String> filters = new ArrayList<>();
        if (width != null && height != null) filters.add("scale=" + width + ":" + height
                + ":force_original_aspect_ratio=decrease,pad=" + width + ":" + height + ":(ow-iw)/2:(oh-ih)/2");
        if ("BURN_IN".equalsIgnoreCase(subtitleMode)) {
            if (subtitle == null || !Files.isRegularFile(subtitle)) {
                throw new IllegalStateException("当前任务没有可烧录的字幕文件");
            }
            filters.add("subtitles='" + subtitle.toAbsolutePath().toString()
                    .replace("\\", "/").replace(":", "\\:").replace("'", "\\'") + "'");
        }
        if (!filters.isEmpty()) command.addAll(List.of("-vf", String.join(",", filters)));
        if (frameRate != null) command.addAll(List.of("-r", trim(frameRate)));
        String requestedEncoder = encoder(preset.videoCodec(), preset.hardwareEncoder());
        String encoder = encoderCapabilities.resolve(requestedEncoder, softwareEncoder(preset.videoCodec()));
        command.addAll(List.of("-c:v", encoder));
        Integer quality = request.qualityValue() != null ? request.qualityValue() : preset.qualityValue();
        Integer bitrate = request.targetBitrateKbps() != null ? request.targetBitrateKbps() : preset.targetBitrateKbps();
        if (bitrate != null) command.addAll(List.of("-b:v", bitrate + "k"));
        else if (quality != null && encoder.contains("nvenc")) command.addAll(List.of("-cq", String.valueOf(quality),
                "-preset", nvencPreset(request.performanceMode())));
        else if (quality != null) command.addAll(List.of("-crf", String.valueOf(quality),
                "-preset", softwarePreset(request.performanceMode())));
        if ("PRORES".equalsIgnoreCase(preset.videoCodec())) command.addAll(List.of("-profile:v", "2"));
        command.addAll(List.of("-map", "0:v:0", "-map", "0:a:0?"));
        if ("SOFT".equalsIgnoreCase(subtitleMode)) {
            command.addAll(List.of("-map", "0:s:0?", "-c:s",
                    "WEBM".equalsIgnoreCase(preset.container()) ? "webvtt" : "mov_text"));
        }
        command.addAll(List.of("-c:a", audioEncoder(preset.audioCodec())));
        if ("AAC".equalsIgnoreCase(preset.audioCodec()) && preset.audioBitrateKbps() != null) {
            command.addAll(List.of("-b:a", preset.audioBitrateKbps() + "k"));
        }
        command.addAll(List.of("-ar", String.valueOf(preset.audioSampleRate()), "-movflags", "+faststart",
                "-progress", "pipe:1", "-nostats", output.toString()));
        run(jobId, command, durationSeconds);
    }

    private String encoder(String codec, String hardware) {
        if (hardware != null && !hardware.isBlank()) return hardware.toLowerCase();
        return softwareEncoder(codec);
    }

    private String softwareEncoder(String codec) {
        return switch (codec.toUpperCase()) {
            case "HEVC" -> "libx265";
            case "AV1" -> "libsvtav1";
            case "VP9" -> "libvpx-vp9";
            case "PRORES" -> "prores_ks";
            default -> "libx264";
        };
    }

    private String audioEncoder(String codec) {
        return switch (codec.toUpperCase()) {
            case "OPUS" -> "libopus";
            case "PCM" -> "pcm_s16le";
            default -> "aac";
        };
    }

    private String nvencPreset(String mode) {
        if ("SPEED".equalsIgnoreCase(mode)) return "p1";
        if ("QUALITY".equalsIgnoreCase(mode)) return "p7";
        return "p4";
    }

    private String softwarePreset(String mode) {
        if ("SPEED".equalsIgnoreCase(mode)) return "veryfast";
        if ("QUALITY".equalsIgnoreCase(mode)) return "slow";
        return "medium";
    }

    private void registerArtifact(UUID jobId, Path output) throws Exception {
        var row = jdbc.queryForMap("""
                SELECT ej.project_id, ej.revision_id, ej.requested_by
                FROM export_job ej WHERE ej.id=?
                """, jobId);
        UUID artifactId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO artifact(id,owner_id,project_id,revision_id,generation_run_id,
                artifact_type,storage_key,mime_type,size_bytes,sha256,schema_version,temporary,
                expires_at,created_at,deleted_at)
                VALUES(?,?,?,?,NULL,'EXPORT',?,?,?,?,NULL,TRUE,?,?,NULL)
                """, artifactId, row.get("REQUESTED_BY"), row.get("PROJECT_ID"), row.get("REVISION_ID"),
                output.toAbsolutePath().toString(), mime(output), Files.size(output), sha256(output),
                OffsetDateTime.now().plusDays(7), OffsetDateTime.now());
        jdbc.update("UPDATE export_job SET output_artifact_id=? WHERE id=?", artifactId, jobId);
    }

    private void update(UUID id, String status, int progress, String error) {
        jdbc.update("UPDATE export_job SET status=?, progress=?, error_message=?, started_at=COALESCE(started_at,?) WHERE id=?",
                status, progress, error, OffsetDateTime.now(), id);
    }

    private void run(UUID jobId, List<String> command, double durationSeconds) {
        try {
            log.info("EXPORT_FFMPEG command={}", command);
            FfmpegProgressParser progressParser = new FfmpegProgressParser(durationSeconds);
            java.util.concurrent.atomic.AtomicInteger lastProgress = new java.util.concurrent.atomic.AtomicInteger(10);
            var result = cn.longer233.gamenarrator.common.ExternalProcessRunner.run(
                    command, Duration.ofHours(2), null, line -> progressParser.parsePercent(line).ifPresent(progress -> {
                        int previous = lastProgress.getAndUpdate(current -> Math.max(current, progress));
                        if (progress > previous) {
                            try {
                                update(jobId, "RUNNING", progress, null);
                            } catch (RuntimeException exception) {
                                log.warn("EXPORT_PROGRESS_UPDATE_FAILED jobId={} progress={} message={}",
                                        jobId, progress, exception.getMessage());
                            }
                        }
                    }));
            if (result.exitCode() != 0) {
                throw new IllegalStateException("FFmpeg 导出失败：" + tail(result.output(), 1800));
            }
        } catch (cn.longer233.gamenarrator.common.ExternalProcessRunner.ProcessTimeoutException exception) {
            throw new IllegalStateException("导出超时", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("导出被中断", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("无法启动 FFmpeg：" + exception.getMessage(), exception);
        }
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String mime(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".srt")) return "application/x-subrip";
        if (name.endsWith(".wav")) return "audio/wav";
        if (name.endsWith(".webm")) return "video/webm";
        if (name.endsWith(".mov")) return "video/quicktime";
        return "video/mp4";
    }

    private String trim(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : Double.toString(value);
    }

    private String tail(String value, int limit) {
        return value.length() <= limit ? value : value.substring(value.length() - limit);
    }
}
