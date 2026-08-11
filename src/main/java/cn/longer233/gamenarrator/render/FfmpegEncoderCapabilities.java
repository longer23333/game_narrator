package cn.longer233.gamenarrator.render;

import cn.longer233.gamenarrator.common.ExternalProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FfmpegEncoderCapabilities {
    private static final Logger log = LoggerFactory.getLogger(FfmpegEncoderCapabilities.class);
    private final String ffmpegCommand;
    private final Probe probe;
    private final Map<String, ProbeResult> results = new ConcurrentHashMap<>();

    @Autowired
    public FfmpegEncoderCapabilities(@Value("${game-narrator.ffmpeg-command}") String ffmpegCommand) {
        this(ffmpegCommand, FfmpegEncoderCapabilities::runProbe);
    }

    FfmpegEncoderCapabilities(String ffmpegCommand, Probe probe) {
        this.ffmpegCommand = ffmpegCommand;
        this.probe = probe;
    }

    public String resolve(String requested, String softwareFallback) {
        String preferred = normalize(requested);
        String fallback = normalize(softwareFallback);
        if (!hardware(preferred)) return preferred;
        ProbeResult preferredResult = results.computeIfAbsent(preferred,
                encoder -> probe.test(ffmpegCommand, encoder));
        if (preferredResult.available()) return preferred;
        ProbeResult fallbackResult = results.computeIfAbsent(fallback,
                encoder -> probe.test(ffmpegCommand, encoder));
        if (!fallbackResult.available()) {
            throw new IllegalStateException("FFmpeg 硬件编码器 " + preferred + " 不可用（"
                    + preferredResult.message() + "），软件编码器 " + fallback + " 也不可用（"
                    + fallbackResult.message() + "）。请检查 FFmpeg 组件和显卡驱动。");
        }
        log.warn("FFMPEG_ENCODER_UNAVAILABLE requested={} fallback={} reason={}",
                preferred, fallback, preferredResult.message());
        return fallback;
    }

    private static ProbeResult runProbe(String ffmpeg, String encoder) {
        try {
            var result = ExternalProcessRunner.run(List.of(ffmpeg, "-hide_banner", "-loglevel", "error",
                    "-f", "lavfi", "-i", "color=c=black:s=64x64:d=0.05", "-frames:v", "1",
                    "-an", "-c:v", encoder, "-f", "null", "-"), Duration.ofSeconds(20));
            if (result.exitCode() == 0) return new ProbeResult(true, "可用");
            return new ProbeResult(false, tail(result.output(), 500));
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            return new ProbeResult(false, message);
        }
    }

    private static boolean hardware(String encoder) {
        return encoder.contains("_nvenc") || encoder.contains("_qsv") || encoder.contains("_amf");
    }

    private static String normalize(String encoder) {
        if (encoder == null || encoder.isBlank()) throw new IllegalArgumentException("FFmpeg 编码器不能为空");
        return encoder.trim().toLowerCase(Locale.ROOT);
    }

    private static String tail(String value, int maximum) {
        String normalized = value == null || value.isBlank() ? "未返回详细信息" : value.trim();
        return normalized.length() <= maximum ? normalized : normalized.substring(normalized.length() - maximum);
    }

    @FunctionalInterface
    interface Probe { ProbeResult test(String ffmpegCommand, String encoder); }
    record ProbeResult(boolean available, String message) { }
}
