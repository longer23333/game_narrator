package cn.longer233.gamenarrator.task.application;

import cn.longer233.gamenarrator.storage.VideoStorage;
import cn.longer233.gamenarrator.pipeline.VideoTaskEngine;
import cn.longer233.gamenarrator.effect.EffectRerenderWorker;
import cn.longer233.gamenarrator.effect.EffectSettingsRequest;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class VideoTaskService {

    private static final Logger log = LoggerFactory.getLogger(VideoTaskService.class);
    private final VideoTaskRepository repository;
    private final VideoStorage storage;
    private final VideoTaskEngine engine;
    private final EffectRerenderWorker effectRerenderWorker;
    private final ProjectHistoryService projectHistoryService;
    private final Path storageRoot;

    public VideoTaskService(
            VideoTaskRepository repository,
            VideoStorage storage,
            VideoTaskEngine engine,
            EffectRerenderWorker effectRerenderWorker,
            ProjectHistoryService projectHistoryService,
            @org.springframework.beans.factory.annotation.Value("${game-narrator.storage-root}") String storageRoot
    ) {
        this.repository = repository;
        this.storage = storage;
        this.engine = engine;
        this.effectRerenderWorker = effectRerenderWorker;
        this.projectHistoryService = projectHistoryService;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @Transactional
    public VideoTaskView create(CreateVideoTaskCommand command, MultipartFile video)
            throws IOException {
        log.info("TASK_CREATE_BEGIN name={} category={} style={} targetSeconds={}",
                command.name(),
                command.gameCategory(),
                command.commentaryStyle(),
                command.targetDurationSeconds());
        String videoPath = storage.save(video);
        VideoTask task = new VideoTask(
                command.name(),
                command.gameCategory(),
                command.commentaryStyle(),
                command.targetDurationSeconds(),
                command.taskBrief(),
                videoPath,
                command.storyboardReviewEnabled()
        );
        VideoTask savedTask = repository.saveAndFlush(task);
        savedTask.configureEditingScope(command.editingScope());
        savedTask.configureTerminologyGlossary(command.terminologyGlossary());
        savedTask.configureAiOptions(command.automaticGenerationEnabled(), command.cloudVisionEnabled(), command.aiScriptEnabled(),
                command.aiVoiceEnabled(), command.autoAssetsEnabled());
        repository.saveAndFlush(savedTask);
        projectHistoryService.createInitialHistory(savedTask);
        log.info("TASK_CREATE_SUCCESS taskId={} stageCount={} videoPath={}",
                savedTask.getId(), savedTask.getStages().size(), videoPath);
        VideoTaskView createdTask = VideoTaskView.from(savedTask);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { engine.start(savedTask.getId()); }
        });
        return createdTask;
    }

    @Transactional
    public VideoTaskView find(UUID id) {
        log.debug("TASK_FIND taskId={}", id);
        return repository.findById(id)
                .map(VideoTaskView::from)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Transactional
    public List<VideoTaskView> findAll() {
        log.debug("TASK_LIST");
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(VideoTaskView::from)
                .toList();
    }

    @Transactional
    public VideoTaskView rename(UUID id, RenameTaskRequest request) {
        VideoTask task = repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        task.rename(request.name());
        projectHistoryService.renameProject(id, task.getName());
        log.info("TASK_RENAMED taskId={}", id);
        return VideoTaskView.from(task);
    }

    public void start(UUID id) {
        if (!repository.existsById(id)) {
            throw new TaskNotFoundException(id);
        }
        log.info("TASK_MANUAL_START taskId={}", id);
        engine.start(id);
    }

    @Transactional
    public VideoTaskView cancel(UUID id) {
        VideoTask task = repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        if (task.getStatus() != cn.longer233.gamenarrator.task.domain.TaskStatus.PROCESSING) {
            throw new IllegalStateException("只有正在处理的任务可以取消");
        }
        engine.requestCancellation(id);
        log.info("TASK_CANCEL_REQUESTED taskId={}", id);
        return VideoTaskView.from(task);
    }

    @Transactional
    public VideoTaskView retry(UUID id) {
        VideoTask task = repository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        task.prepareRetry();
        repository.save(task);
        log.info("TASK_RETRY_ACCEPTED taskId={}", id);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                engine.start(id);
            }
        });
        return VideoTaskView.from(task);
    }

    @Transactional
    public VideoTaskView approveStoryboard(UUID id) {
        VideoTask task = repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        task.approveStoryboard();
        repository.save(task);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { engine.start(id); }
        });
        log.info("STORYBOARD_APPROVED taskId={}", id);
        return VideoTaskView.from(task);
    }

    public void rerenderEffects(UUID id, EffectSettingsRequest settings) {
        if (!repository.existsById(id)) throw new TaskNotFoundException(id);
        log.info("TASK_EFFECT_RERENDER taskId={}", id);
        effectRerenderWorker.rerender(id, settings);
    }

    @Transactional
    public Path renderedVideo(UUID id) {
        VideoTask task = repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        if (task.getRenderedVideoPath() == null) {
            throw new IllegalStateException("该任务尚未生成最终视频");
        }
        Path output = Path.of(task.getRenderedVideoPath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(output)) {
            throw new IllegalStateException("最终视频文件不存在：" + output);
        }
        return output;
    }

    @Transactional
    public Path sourceVideo(UUID id) {
        VideoTask task = repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        Path source = Path.of(task.getSourceVideoPath()).toAbsolutePath().normalize();
        if (!source.startsWith(storageRoot) || !Files.isRegularFile(source)) {
            throw new IllegalStateException("源视频文件不存在或不属于任务存储目录");
        }
        return source;
    }

    @Transactional
    public void delete(UUID id) {
        VideoTask task = repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        if (task.getStatus() == cn.longer233.gamenarrator.task.domain.TaskStatus.READY
                || task.getStatus() == cn.longer233.gamenarrator.task.domain.TaskStatus.PROCESSING) {
            engine.requestDeletion(id);
        }
        List<String> paths = java.util.stream.Stream.of(task.getSourceVideoPath(), task.getExtractedAudioPath(),
                task.getSceneManifestPath(), task.getTranscriptTextPath(), task.getSubtitlePath(),
                task.getTranscriptJsonPath(), task.getVisualAnalysisPath(), task.getHighlightManifestPath(),
                task.getGeneratedScriptPath(), task.getVoiceManifestPath(), task.getTimelinePath(),
                task.getGeneratedSubtitlePath(), task.getRenderedVideoPath())
                .filter(java.util.Objects::nonNull).filter(value -> !value.isBlank()).toList();
        Path taskDirectory = storageRoot.resolve("tasks").resolve(id.toString()).normalize();
        repository.delete(task);
        repository.flush();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                paths.forEach(VideoTaskService.this::deleteOwnedArtifact);
                deleteOwnedTree(taskDirectory);
                log.info("TASK_DELETED taskId={} artifactCandidates={}", id, paths.size());
            }
        });
    }

    private void deleteOwnedArtifact(String value) {
        try {
            Path path = Path.of(value).toAbsolutePath().normalize();
            if (!safeOwnedPath(path, storageRoot)) {
                log.warn("TASK_ARTIFACT_DELETE_SKIPPED reason=outside_storage_root");
                return;
            }
            Files.deleteIfExists(path);
        } catch (Exception exception) {
            log.warn("TASK_ARTIFACT_DELETE_SKIPPED reason={}", exception.getClass().getSimpleName());
        }
    }

    private void deleteOwnedTree(Path candidate) {
        Path path = candidate.toAbsolutePath().normalize();
        Path taskRoot = storageRoot.resolve("tasks").normalize();
        if (!safeOwnedPath(path, taskRoot)) {
            log.warn("TASK_DIRECTORY_DELETE_SKIPPED reason=outside_task_root");
            return;
        }
        if (!Files.exists(path)) return;
        try (var entries = Files.walk(path)) {
            for (Path entry : entries.sorted(java.util.Comparator.reverseOrder()).toList()) {
                if (Files.isSymbolicLink(entry)) {
                    Files.deleteIfExists(entry);
                    continue;
                }
                Files.deleteIfExists(entry);
            }
        } catch (Exception exception) {
            log.warn("TASK_DIRECTORY_DELETE_SKIPPED reason={}", exception.getClass().getSimpleName());
        }
    }

    private boolean safeOwnedPath(Path candidate, Path allowedRoot) {
        Path path = candidate.toAbsolutePath().normalize();
        Path root = allowedRoot.toAbsolutePath().normalize();
        if (!path.startsWith(root) || path.equals(root)) return false;
        Path current = root;
        for (Path segment : root.relativize(path)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) return false;
        }
        return true;
    }
}
