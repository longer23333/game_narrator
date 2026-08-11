package cn.longer233.gamenarrator.task.application;

import cn.longer233.gamenarrator.storage.VideoStorage;
import cn.longer233.gamenarrator.pipeline.VideoTaskEngine;
import cn.longer233.gamenarrator.effect.EffectRerenderWorker;
import cn.longer233.gamenarrator.effect.EffectSettingsRequest;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import jakarta.transaction.Transactional;
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
    private final cn.longer233.gamenarrator.common.StorageCleanupService storageCleanup;
    private final cn.longer233.gamenarrator.observability.StorageCapacityGuard capacityGuard;
    private final cn.longer233.gamenarrator.storage.SourceMediaRegistry sourceMediaRegistry;
    private final cn.longer233.gamenarrator.identity.CurrentUserContext currentUser;
    private final cn.longer233.gamenarrator.cloud.CloudSyncService cloudSync;

    public VideoTaskService(
            VideoTaskRepository repository,
            VideoStorage storage,
            VideoTaskEngine engine,
            EffectRerenderWorker effectRerenderWorker,
            ProjectHistoryService projectHistoryService,
            cn.longer233.gamenarrator.common.StorageCleanupService storageCleanup,
            @org.springframework.beans.factory.annotation.Value("${game-narrator.storage-root}") String storageRoot,
            cn.longer233.gamenarrator.observability.StorageCapacityGuard capacityGuard,
            cn.longer233.gamenarrator.storage.SourceMediaRegistry sourceMediaRegistry,
            cn.longer233.gamenarrator.identity.CurrentUserContext currentUser,
            cn.longer233.gamenarrator.cloud.CloudSyncService cloudSync
    ) {
        this.repository = repository;
        this.storage = storage;
        this.engine = engine;
        this.effectRerenderWorker = effectRerenderWorker;
        this.projectHistoryService = projectHistoryService;
        this.storageCleanup = storageCleanup;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.capacityGuard = capacityGuard;
        this.sourceMediaRegistry = sourceMediaRegistry;
        this.currentUser = currentUser;
        this.cloudSync = cloudSync;
    }

    @Transactional
    public VideoTaskView create(CreateVideoTaskCommand command, MultipartFile video)
            throws IOException {
        log.info("TASK_CREATE_BEGIN name={} category={} style={} targetSeconds={}",
                command.name(),
                command.gameCategory(),
                command.commentaryStyle(),
                command.targetDurationSeconds());
        capacityGuard.requireWorkingCapacityAfterUpload(video == null ? 0 : video.getSize());
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
        task.assignOwnership(currentUser.userId());
        VideoTask savedTask = repository.saveAndFlush(task);
        sourceMediaRegistry.registerManaged(savedTask.getId(), Path.of(videoPath),
                video.getOriginalFilename(), video.getContentType());
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
    public VideoTaskView createPendingRemote(CreateVideoTaskCommand command, String pendingVideoPath) {
        capacityGuard.requireTaskCapacity(0);
        VideoTask task = new VideoTask(command.name(), command.gameCategory(), command.commentaryStyle(),
                command.targetDurationSeconds(), command.taskBrief(), pendingVideoPath,
                command.storyboardReviewEnabled());
        task.assignOwnership(currentUser.userId());
        task.configureEditingScope(command.editingScope());
        task.configureTerminologyGlossary(command.terminologyGlossary());
        task.configureAiOptions(command.automaticGenerationEnabled(), command.cloudVisionEnabled(),
                command.aiScriptEnabled(), command.aiVoiceEnabled(), command.autoAssetsEnabled());
        task.startIngestion();
        VideoTask saved = repository.saveAndFlush(task);
        projectHistoryService.createInitialHistory(saved);
        return VideoTaskView.from(saved);
    }

    @Transactional
    public void updateRemoteDownloadProgress(UUID id, int progress) {
        owned(id)
                .updateStageProgress(cn.longer233.gamenarrator.task.domain.ProcessingStageType.VIDEO_INGESTION,
                        Math.max(10, Math.min(99, progress)));
    }

    @Transactional
    public void failRemoteDownload(UUID id, String reason) {
        owned(id).failIngestion(reason);
    }

    public void startDownloadedRemote(UUID id) {
        engine.start(id);
    }

    @Transactional
    public VideoTaskView find(UUID id) {
        log.debug("TASK_FIND taskId={}", id);
        return repository.findByIdAndOwnerId(id, currentUser.userId())
                .map(VideoTaskView::from)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Transactional
    public List<VideoTaskView> findAll() {
        log.debug("TASK_LIST");
        return repository.findAllByOwnerIdOrderByCreatedAtDesc(currentUser.userId())
                .stream()
                .map(VideoTaskView::from)
                .toList();
    }

    @Transactional
    public VideoTaskView rename(UUID id, RenameTaskRequest request) {
        VideoTask task = owned(id);
        task.rename(request.name());
        projectHistoryService.renameProject(id, task.getName());
        log.info("TASK_RENAMED taskId={}", id);
        return VideoTaskView.from(task);
    }

    public void start(UUID id) {
        if (!repository.existsByIdAndOwnerId(id, currentUser.userId())) {
            throw new TaskNotFoundException(id);
        }
        sourceVideo(id);
        log.info("TASK_MANUAL_START taskId={}", id);
        engine.start(id);
    }

    @Transactional
    public VideoTaskView cancel(UUID id) {
        VideoTask task = owned(id);
        if (task.getStatus() != cn.longer233.gamenarrator.task.domain.TaskStatus.PROCESSING) {
            throw new IllegalStateException("只有正在处理的任务可以取消");
        }
        engine.requestCancellation(id);
        log.info("TASK_CANCEL_REQUESTED taskId={}", id);
        return VideoTaskView.from(task);
    }

    @Transactional
    public VideoTaskView retry(UUID id) {
        VideoTask task = owned(id);
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
    public void regenerateAfterConfirmedEventChange(UUID id) {
        VideoTask task = repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        if (task.isAwaitingScriptRegeneration()) {
            log.info("TASK_EVENT_REGENERATION_COALESCED taskId={}", id);
            return;
        }
        task.invalidateAfterConfirmedEventChange();
        repository.save(task);
        log.info("TASK_INVALIDATED_BY_CONFIRMED_EVENT taskId={} restartStage=SCRIPT_GENERATION", id);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { engine.start(id); }
        });
    }

    @Transactional
    public VideoTaskView approveStoryboard(UUID id) {
        VideoTask task = owned(id);
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
        if (!repository.existsByIdAndOwnerId(id, currentUser.userId())) throw new TaskNotFoundException(id);
        log.info("TASK_EFFECT_RERENDER taskId={}", id);
        effectRerenderWorker.rerender(id, settings);
    }

    @Transactional
    public Path renderedVideo(UUID id) {
        VideoTask task = owned(id);
        if (task.getRenderedVideoPath() == null) {
            throw new IllegalStateException("该任务尚未生成最终视频");
        }
        Path output = Path.of(task.getRenderedVideoPath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(output)) {
            output = cloudSync.restoreLatestArtifact(id, "RENDERED_VIDEO",
                    storageRoot.resolve("tasks").resolve(id.toString()).resolve("cloud-rendered.mp4"));
        }
        return output;
    }

    @Transactional
    public Path sourceVideo(UUID id) {
        VideoTask task = owned(id);
        Path source = Path.of(task.getSourceVideoPath()).toAbsolutePath().normalize();
        if (!source.startsWith(storageRoot) || !Files.isRegularFile(source)) {
            source = cloudSync.restoreSource(id,
                    storageRoot.resolve("sources").resolve(id + ".cloud-media"));
        }
        return source;
    }

    @Transactional
    public void delete(UUID id) {
        VideoTask task = owned(id);
        if (task.getStatus() == cn.longer233.gamenarrator.task.domain.TaskStatus.READY
                || task.getStatus() == cn.longer233.gamenarrator.task.domain.TaskStatus.PROCESSING) {
            engine.requestDeletion(id);
            cn.longer233.gamenarrator.common.TaskProcessRegistry.cancelAndAwait(id,
                    java.time.Duration.ofSeconds(5));
        }
        task.moveToTrash();
        projectHistoryService.moveProjectToTrash(id);
        repository.saveAndFlush(task);
        log.info("TASK_MOVED_TO_TRASH taskId={}", id);
    }

    private VideoTask owned(UUID id) {
        return repository.findByIdAndOwnerId(id, currentUser.userId()).orElseThrow(() -> new TaskNotFoundException(id));
    }

}
