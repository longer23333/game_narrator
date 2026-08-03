package cn.longer233.gamenarrator.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FfmpegMediaProbe {

    private static final Logger log = LoggerFactory.getLogger(FfmpegMediaProbe.class);
    private static final Pattern DURATION =
            Pattern.compile("Duration:\\s*(\\d+):(\\d+):([\\d.]+)");
    private static final Pattern VIDEO =
            Pattern.compile("Video:\\s*([^,\\s]+).*?(\\d{2,5})x(\\d{2,5}).*?([\\d.]+)\\s*fps");
    private static final Pattern AUDIO =
            Pattern.compile("Audio:\\s*([^,\\s]+)");

    private final String ffmpegCommand;

    public FfmpegMediaProbe(@Value("${game-narrator.ffmpeg-command}") String ffmpegCommand) {
        this.ffmpegCommand = ffmpegCommand;
    }

    public MediaMetadata inspect(Path videoPath) {
        log.info("MEDIA_PROBE_BEGIN path={}", videoPath);
        try {
            var result = cn.longer233.gamenarrator.common.ExternalProcessRunner.run(List.of(
                    ffmpegCommand, "-hide_banner", "-i", videoPath.toString()), Duration.ofSeconds(30));
            String output = result.output();
            log.debug("MEDIA_PROBE_RAW_OUTPUT%n{}", output);
            MediaMetadata metadata = parse(output);
            log.info("MEDIA_PROBE_SUCCESS duration={} resolution={}x{} fps={} videoCodec={} audioCodec={}",
                    metadata.durationSeconds(),
                    metadata.width(),
                    metadata.height(),
                    metadata.framesPerSecond(),
                    metadata.videoCodec(),
                    metadata.audioCodec());
            return metadata;
        } catch (Exception exception) {
            log.error("MEDIA_PROBE_FAILED path={} type={} message={}",
                    videoPath, exception.getClass().getName(), exception.getMessage(), exception);
            throw new IllegalStateException("无法读取视频媒体信息：" + exception.getMessage(), exception);
        }
    }

    MediaMetadata parse(String output) {
        Matcher durationMatcher = DURATION.matcher(output);
        Matcher videoMatcher = VIDEO.matcher(output);
        Matcher audioMatcher = AUDIO.matcher(output);
        if (!durationMatcher.find()) {
            throw new IllegalArgumentException("FFmpeg 输出中没有视频时长");
        }
        if (!videoMatcher.find()) {
            throw new IllegalArgumentException("FFmpeg 输出中没有可识别的视频流");
        }

        double duration = Integer.parseInt(durationMatcher.group(1)) * 3600
                + Integer.parseInt(durationMatcher.group(2)) * 60
                + Double.parseDouble(durationMatcher.group(3));
        String audioCodec = audioMatcher.find() ? audioMatcher.group(1) : "none";
        return new MediaMetadata(
                duration,
                Integer.parseInt(videoMatcher.group(2)),
                Integer.parseInt(videoMatcher.group(3)),
                Double.parseDouble(videoMatcher.group(4)),
                videoMatcher.group(1),
                audioCodec
        );
    }
}
