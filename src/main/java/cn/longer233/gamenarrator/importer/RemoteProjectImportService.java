package cn.longer233.gamenarrator.importer;

import cn.longer233.gamenarrator.common.SecurePathGuard;
import cn.longer233.gamenarrator.task.application.CreateVideoTaskCommand;
import cn.longer233.gamenarrator.task.application.VideoTaskService;
import cn.longer233.gamenarrator.task.application.VideoTaskView;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executor;

@Service
public class RemoteProjectImportService {
    private final YtDlpMediaImporter importer;
    private final VideoTaskService tasks;
    private final Executor executor;
    private final Path taskRoot;

    public RemoteProjectImportService(YtDlpMediaImporter importer, VideoTaskService tasks,
            @Qualifier("taskExecutor") Executor executor,
            @Value("${game-narrator.storage-root}") String storageRoot) {
        this.importer = importer;
        this.tasks = tasks;
        this.executor = executor;
        this.taskRoot = Path.of(storageRoot).toAbsolutePath().normalize().resolve("tasks");
    }

    public VideoTaskView start(RemoteProjectImportRequest request) {
        CreateVideoTaskCommand command = new CreateVideoTaskCommand(request.name(), request.gameCategory(),
                request.commentaryStyle(), request.targetDurationSeconds(), request.editingScope(),
                request.taskBrief(), request.terminologyGlossary(), request.storyboardReviewEnabled(),
                request.automaticGenerationEnabled(), request.cloudVisionEnabled(), request.aiScriptEnabled(),
                request.aiVoiceEnabled(), request.autoAssetsEnabled());
        Path pending = taskRoot.resolve("pending-" + java.util.UUID.randomUUID() + ".mp4");
        VideoTaskView task = tasks.createPendingRemote(command, pending.toString());
        executor.execute(() -> download(task, request.media(), pending));
        return task;
    }

    private void download(VideoTaskView task, MediaDownloadRequest request, Path pending) {
        try {
            MediaDownloadResult result = importer.download(request, progress ->
                    tasks.updateRemoteDownloadProgress(task.id(), percent(progress.percent())));
            Path source = Path.of(result.localPath()).toAbsolutePath().normalize();
            Path safeRoot = SecurePathGuard.prepareRoot(taskRoot);
            Files.createDirectories(taskRoot);
            if (!SecurePathGuard.isOwned(pending, safeRoot)) throw new IllegalStateException("非法项目路径");
            if (result.assetId() == null) {
                Files.move(source, pending, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(source, pending, StandardCopyOption.REPLACE_EXISTING);
            }
            if (result.platformSubtitlePath() != null) {
                Path subtitle = Path.of(result.platformSubtitlePath()).toAbsolutePath().normalize();
                if (Files.isRegularFile(subtitle)) Files.move(subtitle,
                        pending.resolveSibling(pending.getFileName() + ".platform.srt"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            tasks.startDownloadedRemote(task.id());
        } catch (Exception exception) {
            tasks.failRemoteDownload(task.id(), "下载失败：" + exception.getMessage());
        }
    }

    private int percent(String value) {
        try { return (int) Math.floor(Double.parseDouble(value.replace("%", "").trim())); }
        catch (Exception ignored) { return 10; }
    }
}
