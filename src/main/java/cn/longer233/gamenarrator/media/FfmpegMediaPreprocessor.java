package cn.longer233.gamenarrator.media;

import cn.longer233.gamenarrator.audio.AudioAnalysisService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FfmpegMediaPreprocessor {

    private static final Logger log = LoggerFactory.getLogger(FfmpegMediaPreprocessor.class);
    private static final Pattern PTS_TIME = Pattern.compile("pts_time:([0-9]+(?:\\.[0-9]+)?)");
    private final String ffmpegCommand;
    private final Path storageRoot;
    private final ObjectMapper objectMapper;
    private final double sceneThreshold;
    private final int sceneAnalysisFps;
    private final int maximumSceneFrames;
    private final Duration sceneTimeout;
    private final AudioAnalysisService audioAnalysis;

    public FfmpegMediaPreprocessor(
            @Value("${game-narrator.ffmpeg-command}") String ffmpegCommand,
            @Value("${game-narrator.storage-root}") String storageRoot,
            @Value("${game-narrator.scene-threshold:0.35}") double sceneThreshold,
            @Value("${game-narrator.scene-analysis-fps:6}") int sceneAnalysisFps,
            @Value("${game-narrator.maximum-scene-frames:240}") int maximumSceneFrames,
            @Value("${game-narrator.scene-timeout-minutes:20}") int sceneTimeoutMinutes,
            ObjectMapper objectMapper,
            AudioAnalysisService audioAnalysis
    ) {
        this.ffmpegCommand = ffmpegCommand;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.sceneThreshold = sceneThreshold;
        this.sceneAnalysisFps = Math.max(1, Math.min(12, sceneAnalysisFps));
        this.maximumSceneFrames = Math.max(10, Math.min(1000, maximumSceneFrames));
        this.sceneTimeout = Duration.ofMinutes(Math.max(2, Math.min(120, sceneTimeoutMinutes)));
        this.objectMapper = objectMapper;
        this.audioAnalysis = audioAnalysis;
    }

    public MediaPreparationResult prepare(
            java.util.UUID taskId,
            Path sourceVideo,
            boolean hasAudio
    ) {
        Path taskDirectory = storageRoot.resolve("tasks").resolve(taskId.toString());
        Path sceneDirectory = taskDirectory.resolve("scenes");
        Path audioPath = taskDirectory.resolve("speech-16k.wav");
        Path manifestPath = taskDirectory.resolve("scenes.json");
        try {
            Files.createDirectories(sceneDirectory);
            if (hasAudio) {
                extractAudio(sourceVideo, audioPath);
                audioAnalysis.analyze(audioPath);
            }
            List<SceneFrame> scenes = detectScenes(sourceVideo, sceneDirectory);
            cn.longer233.gamenarrator.common.AtomicArtifactWriter.writeJson(objectMapper, manifestPath, scenes);
            log.info("MEDIA_PREPARATION_SUCCESS taskId={} sceneCount={} audioExtracted={} manifest={}",
                    taskId, scenes.size(), hasAudio, manifestPath);
            return new MediaPreparationResult(
                    hasAudio ? audioPath.toString() : null,
                    manifestPath.toString(),
                    scenes
            );
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建媒体预处理产物：" + exception.getMessage(), exception);
        }
    }

    private void extractAudio(Path sourceVideo, Path outputPath) {
        log.info("AUDIO_EXTRACTION_BEGIN source={} output={}", sourceVideo, outputPath);
        run(List.of(
                ffmpegCommand, "-nostdin", "-y", "-hide_banner", "-loglevel", "warning", "-threads", "0",
                "-i", sourceVideo.toString(), "-vn",
                "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le",
                outputPath.toString()
        ), Duration.ofMinutes(30), "音频提取");
        log.info("AUDIO_EXTRACTION_SUCCESS output={} sizeBytes={}",
                outputPath, fileSize(outputPath));
    }

    private List<SceneFrame> detectScenes(Path sourceVideo, Path sceneDirectory) {
        log.info("SCENE_DETECTION_BEGIN source={} threshold={} output={}",
                sourceVideo, sceneThreshold, sceneDirectory);
        clearSceneImages(sceneDirectory);
        String outputPattern = sceneDirectory.resolve("scene-%04d.jpg").toString();
        String output = run(List.of(
                ffmpegCommand, "-nostdin", "-y", "-hide_banner", "-threads", "0",
                "-i", sourceVideo.toString(),
                "-an", "-sn", "-dn",
                "-vf", "fps=" + sceneAnalysisFps + ",scale=480:-2:flags=fast_bilinear,select=gt(scene\\," + sceneThreshold + "),showinfo",
                "-fps_mode", "vfr", "-frames:v", String.valueOf(maximumSceneFrames),
                "-c:v", "mjpeg", "-q:v", "5", "-threads:v", "1",
                outputPattern
        ), sceneTimeout, "场景检测");

        List<Double> timestamps = PTS_TIME.matcher(output).results()
                .map(result -> Double.parseDouble(result.group(1)))
                .toList();
        try (var paths = Files.list(sceneDirectory)) {
            List<Path> images = paths
                    .filter(path -> path.getFileName().toString().endsWith(".jpg"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            if (images.isEmpty()) {
                Path first = sceneDirectory.resolve("scene-0001.jpg");
                run(List.of(ffmpegCommand, "-nostdin", "-y", "-hide_banner", "-loglevel", "warning",
                        "-ss", "0", "-i", sourceVideo.toString(), "-an", "-frames:v", "1",
                        "-vf", "scale=480:-2:flags=fast_bilinear", "-c:v", "mjpeg", "-q:v", "5", "-threads:v", "1",
                        "-update", "1",
                        first.toString()), Duration.ofMinutes(2), "首帧提取");
                images = List.of(first);
            }
            List<SceneFrame> scenes = new ArrayList<>();
            for (int index = 0; index < images.size(); index++) {
                double timestamp = index < timestamps.size() ? timestamps.get(index) : 0.0;
                scenes.add(new SceneFrame(index + 1, timestamp, images.get(index).toString()));
            }
            log.info("SCENE_DETECTION_SUCCESS sceneCount={}", scenes.size());
            return scenes;
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取场景截图：" + exception.getMessage(), exception);
        }
    }

    private void clearSceneImages(Path sceneDirectory) {
        try (var paths = Files.list(sceneDirectory)) {
            for (Path path : paths.filter(item -> item.getFileName().toString().matches("scene-\\d+\\.(?:jpg|png)"))
                    .toList()) Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("无法清理旧镜头缓存：" + exception.getMessage(), exception);
        }
    }

    private String run(List<String> command, Duration timeout, String operation) {
        try {
            var result = cn.longer233.gamenarrator.common.ExternalProcessRunner.run(command, timeout);
            if (result.exitCode() != 0) {
                throw new IllegalStateException(operation + "失败，FFmpeg 退出码 "
                        + result.exitCode() + "：" + tail(result.output(), 1200));
            }
            return result.output();
        } catch (cn.longer233.gamenarrator.common.ExternalProcessRunner.ProcessTimeoutException exception) {
            throw new IllegalStateException(operation + "超时（" + timeout.toMinutes() + " 分钟）", exception);
        } catch (IOException exception) {
            throw new IllegalStateException(operation + "无法启动 FFmpeg：" + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(operation + "被中断", exception);
        }
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ignored) {
            return -1;
        }
    }

    private String tail(String value, int limit) {
        return value.length() <= limit ? value : value.substring(value.length() - limit);
    }
}
