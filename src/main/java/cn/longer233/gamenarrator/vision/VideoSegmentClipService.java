package cn.longer233.gamenarrator.vision;

import cn.longer233.gamenarrator.common.ExternalProcessRunner;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class VideoSegmentClipService {
    private final VideoTaskRepository repository;
    private final String ffmpegCommand;
    private final Path clipRoot;

    public VideoSegmentClipService(VideoTaskRepository repository,
                                   @Value("${game-narrator.ffmpeg-command}") String ffmpegCommand,
                                   @Value("${game-narrator.storage-root}") String storageRoot) {
        this.repository = repository;
        this.ffmpegCommand = ffmpegCommand;
        this.clipRoot = Path.of(storageRoot).toAbsolutePath().normalize().resolve("segment-clips").normalize();
    }

    public Path create(UUID taskId, double startSeconds, double durationSeconds, boolean mute)
            throws IOException, InterruptedException {
        if (!Double.isFinite(startSeconds) || startSeconds < 0) throw new IllegalArgumentException("开始时间不能小于 0 秒");
        if (!Double.isFinite(durationSeconds) || durationSeconds < 1 || durationSeconds > 600) {
            throw new IllegalArgumentException("剪切时长必须在 1 到 600 秒之间");
        }
        var task = repository.findById(taskId).orElseThrow(() -> new IllegalArgumentException("没有找到所属视频任务"));
        Path source = Path.of(task.getSourceVideoPath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) throw new IllegalStateException("原始视频文件不存在，无法剪切镜头");

        Files.createDirectories(clipRoot);
        String key = ("%s-%.3f-%.3f-%s".formatted(taskId, startSeconds, durationSeconds, mute ? "mute" : "audio")
                .replace('.', '_')) + ".mp4";
        Path output = clipRoot.resolve(key).normalize();
        if (!output.startsWith(clipRoot)) throw new IllegalStateException("剪切输出路径无效");
        if (Files.isRegularFile(output) && Files.size(output) > 0) return output;

        Path temporary = clipRoot.resolve(key + ".part.mp4").normalize();
        List<String> command = new ArrayList<>(List.of(ffmpegCommand, "-y", "-hide_banner", "-loglevel", "warning",
                "-ss", decimal(startSeconds), "-i", source.toString(), "-t", decimal(durationSeconds),
                "-map", "0:v:0", "-c:v", "libx264", "-preset", "veryfast", "-crf", "21",
                "-pix_fmt", "yuv420p", "-movflags", "+faststart"));
        if (mute) command.add("-an");
        else command.addAll(List.of("-map", "0:a:0?", "-c:a", "aac", "-b:a", "160k"));
        command.add(temporary.toString());
        try {
            var result = ExternalProcessRunner.run(command, Duration.ofSeconds(Math.max(90, Math.round(durationSeconds * 3))));
            if (result.exitCode() != 0 || !Files.isRegularFile(temporary) || Files.size(temporary) == 0) {
                throw new IllegalStateException("FFmpeg 镜头剪切失败：" + result.output().strip());
            }
            try {
                Files.move(temporary, output, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return output;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
