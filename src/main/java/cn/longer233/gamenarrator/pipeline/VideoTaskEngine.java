package cn.longer233.gamenarrator.pipeline;

import cn.longer233.gamenarrator.media.FfmpegMediaPreprocessor;
import cn.longer233.gamenarrator.media.FfmpegMediaProbe;
import cn.longer233.gamenarrator.media.MediaMetadata;
import cn.longer233.gamenarrator.media.MediaPreparationResult;
import cn.longer233.gamenarrator.transcription.TranscriptionResult;
import cn.longer233.gamenarrator.transcription.Transcriber;
import cn.longer233.gamenarrator.transcription.PlatformSubtitleReader;
import cn.longer233.gamenarrator.transcription.SubtitleChunkAnalysisService;
import cn.longer233.gamenarrator.common.PhaseRetryExecutor;
import cn.longer233.gamenarrator.vision.VisionAnalyzer;
import cn.longer233.gamenarrator.vision.VideoUnderstandingResult;
import cn.longer233.gamenarrator.vision.VideoSegmentSemanticIndex;
import cn.longer233.gamenarrator.highlight.HighlightSelectionResult;
import cn.longer233.gamenarrator.highlight.RuleBasedHighlightSelector;
import cn.longer233.gamenarrator.script.GeneratedScript;
import cn.longer233.gamenarrator.script.TextGenerator;
import cn.longer233.gamenarrator.script.StoryboardAssetPlacementService;
import cn.longer233.gamenarrator.voice.VoiceSynthesizer;
import cn.longer233.gamenarrator.voice.VoiceGenerationResult;
import cn.longer233.gamenarrator.voice.SilentVoiceGenerator;
import cn.longer233.gamenarrator.timeline.TimelinePlanner;
import cn.longer233.gamenarrator.timeline.TimelinePlanningResult;
import cn.longer233.gamenarrator.render.FfmpegVideoRenderer;
import cn.longer233.gamenarrator.render.RenderResult;
import cn.longer233.gamenarrator.effect.EffectPresetCatalog;
import cn.longer233.gamenarrator.effect.EffectSettingsRequest;
import cn.longer233.gamenarrator.task.domain.ProcessingStageType;
import cn.longer233.gamenarrator.event.GameEventTimelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import cn.longer233.gamenarrator.common.TaskProcessRegistry;

@Service
public class VideoTaskEngine {

    private static final Logger log = LoggerFactory.getLogger(VideoTaskEngine.class);
    private final TaskWorkflowStateService stateService;
    private final FfmpegMediaProbe mediaProbe;
    private final FfmpegMediaPreprocessor mediaPreprocessor;
    private final Transcriber transcriber;
    private final PlatformSubtitleReader platformSubtitleReader;
    private final SubtitleChunkAnalysisService subtitleChunkAnalysis;
    private final PhaseRetryExecutor retryExecutor;
    private final VisionAnalyzer visionClient;
    private final VideoSegmentSemanticIndex segmentSemanticIndex;
    private final RuleBasedHighlightSelector highlightSelector;
    private final TextGenerator scriptGenerator;
    private final VoiceSynthesizer voiceGenerator;
    private final SilentVoiceGenerator silentVoiceGenerator;
    private final TimelinePlanner timelinePlanner;
    private final FfmpegVideoRenderer videoRenderer;
    private final EffectPresetCatalog effectPresetCatalog;
    private final StoryboardAssetPlacementService storyboardAssets;
    private final GameEventTimelineService gameEvents;
    private final PrioritizedTaskExecutor prioritizedTasks;
    private final cn.longer233.gamenarrator.task.repository.VideoTaskRepository taskRepository;
    private final cn.longer233.gamenarrator.observability.TaskAdmissionController admission;
    private final StageCheckpointService checkpoints;
    private final Set<UUID> deletionRequested = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeTasks = ConcurrentHashMap.newKeySet();

    public void requestDeletion(UUID taskId) {
        deletionRequested.add(taskId);
        prioritizedTasks.cancelQueued(taskId);
        TaskProcessRegistry.cancel(taskId);
    }

    public void requestCancellation(UUID taskId) {
        prioritizedTasks.cancelQueued(taskId);
        TaskProcessRegistry.cancel(taskId);
    }

    public VideoTaskEngine(
            TaskWorkflowStateService stateService,
            FfmpegMediaProbe mediaProbe,
            FfmpegMediaPreprocessor mediaPreprocessor,
            Transcriber transcriber,
            PlatformSubtitleReader platformSubtitleReader,
            SubtitleChunkAnalysisService subtitleChunkAnalysis,
            PhaseRetryExecutor retryExecutor,
            VisionAnalyzer visionClient,
            VideoSegmentSemanticIndex segmentSemanticIndex,
            RuleBasedHighlightSelector highlightSelector,
            TextGenerator scriptGenerator,
            VoiceSynthesizer voiceGenerator,
            SilentVoiceGenerator silentVoiceGenerator,
            TimelinePlanner timelinePlanner,
            FfmpegVideoRenderer videoRenderer,
            EffectPresetCatalog effectPresetCatalog,
            StoryboardAssetPlacementService storyboardAssets,
            GameEventTimelineService gameEvents,
            PrioritizedTaskExecutor prioritizedTasks,
            cn.longer233.gamenarrator.task.repository.VideoTaskRepository taskRepository,
            cn.longer233.gamenarrator.observability.TaskAdmissionController admission,
            StageCheckpointService checkpoints
    ) {
        this.stateService = stateService;
        this.mediaProbe = mediaProbe;
        this.mediaPreprocessor = mediaPreprocessor;
        this.transcriber = transcriber;
        this.platformSubtitleReader = platformSubtitleReader;
        this.subtitleChunkAnalysis = subtitleChunkAnalysis;
        this.retryExecutor = retryExecutor;
        this.visionClient = visionClient;
        this.segmentSemanticIndex = segmentSemanticIndex;
        this.highlightSelector = highlightSelector;
        this.scriptGenerator = scriptGenerator;
        this.voiceGenerator = voiceGenerator;
        this.silentVoiceGenerator = silentVoiceGenerator;
        this.timelinePlanner = timelinePlanner;
        this.videoRenderer = videoRenderer;
        this.effectPresetCatalog = effectPresetCatalog;
        this.storyboardAssets = storyboardAssets;
        this.gameEvents = gameEvents;
        this.prioritizedTasks = prioritizedTasks;
        this.taskRepository = taskRepository;
        this.admission = admission;
        this.checkpoints = checkpoints;
    }

    public void start(UUID taskId) {
        int priority = taskRepository.findById(taskId).map(task -> task.getPriority()).orElse(0);
        if (!prioritizedTasks.submit(taskId, priority, () -> process(taskId))) {
            log.debug("ENGINE_SUBMISSION_DUPLICATE_IGNORED taskId={}", taskId);
        }
    }

    public void reprioritize(UUID taskId, int priority) {
        prioritizedTasks.reprioritize(taskId, priority);
        TaskProcessRegistry.reprioritizeResources(taskId, priority);
    }

    private void process(UUID taskId) {
        if (!activeTasks.add(taskId)) {
            log.debug("ENGINE_DUPLICATE_IGNORED taskId={}", taskId);
            return;
        }
        MDC.put("traceId", "task-" + taskId.toString().substring(0, 8));
        log.info("ENGINE_START taskId={}", taskId);
        String activeStage = "VIDEO_INGESTION";
        TaskProcessRegistry.ResourceLease resourceLease = null;
        try (TaskProcessRegistry.Scope ignored = TaskProcessRegistry.open(taskId)) {
            checkCancellation(taskId);
            StageCheckpointService.Validation checkpoint = checkpoints.reconcile(taskId);
            if (!checkpoint.valid()) {
                log.warn("ENGINE_CHECKPOINT_RESTART taskId={} stage={} reason={}", taskId,
                        checkpoint.restartStage(), checkpoint.reason());
            }
            EngineTaskContext context = stateService.context(taskId);
            Path sourcePath = Path.of(context.sourceVideoPath());
            long sourceBytes;
            try { sourceBytes = java.nio.file.Files.size(sourcePath); }
            catch (Exception ignoredSize) { sourceBytes = 0; }
            int priority = taskRepository.findById(taskId).map(task -> task.getPriority()).orElse(0);
            resourceLease = admission.admit(taskId, priority, context, sourceBytes);
            if (!context.ingestionCompleted()) {
                stateService.markIngestionRunning(taskId);
                MediaMetadata metadata = mediaProbe.inspect(sourcePath);
                stateService.markIngestionCompleted(taskId, metadata);
                log.info("ENGINE_STAGE_COMPLETED taskId={} stage=VIDEO_INGESTION", taskId);
                context = stateService.context(taskId);
            } else {
                log.info("ENGINE_STAGE_SKIPPED taskId={} stage=VIDEO_INGESTION reason=already_completed",
                        taskId);
            }

            activeStage = "SCENE_DETECTION";
            checkCancellation(taskId);
            if (!context.sceneDetectionCompleted()) {
                stateService.markSceneDetectionRunning(taskId);
                MediaPreparationResult result = mediaPreprocessor.prepare(
                        taskId, sourcePath, context.hasAudio());
                stateService.markSceneDetectionCompleted(taskId, result);
                log.info("ENGINE_STAGE_COMPLETED taskId={} stage=SCENE_DETECTION sceneCount={}",
                        taskId, result.scenes().size());
                context = stateService.context(taskId);
            } else {
                log.info("ENGINE_STAGE_SKIPPED taskId={} stage=SCENE_DETECTION reason=already_completed",
                        taskId);
            }

            activeStage = "TRANSCRIPTION";
            checkCancellation(taskId);
            if (!context.transcriptionCompleted()) {
                stateService.markTranscriptionRunning(taskId);
                TranscriptionResult result = platformSubtitleReader.read(sourcePath);
                if (result != null) {
                    log.info("TRANSCRIPTION_PLATFORM_SUBTITLE taskId={} subtitle={}", taskId,
                            result.subtitlePath());
                } else if (context.automaticGenerationEnabled() && context.hasAudio() && transcriber.available()) {
                    String extractedAudioPath = context.extractedAudioPath();
                    result = retryExecutor.analysis(() ->
                            transcriber.transcribe(Path.of(extractedAudioPath)));
                } else {
                    result = new TranscriptionResult("", null, null, null);
                    String reason = !context.automaticGenerationEnabled() ? "manual_mode_optional_enhancement"
                            : context.hasAudio() ? "whisper_unavailable" : "no_audio_track";
                    log.info("TRANSCRIPTION_SKIPPED taskId={} reason={}", taskId, reason);
                }
                stateService.markTranscriptionCompleted(taskId, result);
                if (result.subtitlePath() != null) {
                    String subtitlePath = result.subtitlePath();
                    Path analysis = retryExecutor.analysis(() ->
                            subtitleChunkAnalysis.analyze(Path.of(subtitlePath)));
                    if (analysis != null) log.info("SUBTITLE_CHUNK_ANALYSIS taskId={} output={}", taskId, analysis);
                }
                log.info("ENGINE_STAGE_COMPLETED taskId={} stage=TRANSCRIPTION characterCount={}",
                        taskId, result.text().length());
                context = stateService.context(taskId);
            } else {
                log.info("ENGINE_STAGE_SKIPPED taskId={} stage=TRANSCRIPTION reason=already_completed",
                        taskId);
            }

            activeStage = "VIDEO_UNDERSTANDING";
            checkCancellation(taskId);
            if (!context.videoUnderstandingCompleted()) {
                if (context.cloudVisionEnabled() && !visionClient.available()) {
                    String reason = "当前视觉服务不可用：请检查云端 API Key/服务状态，或安装并启动本地视觉模型";
                    stateService.deferVideoUnderstanding(taskId, reason);
                    log.warn("ENGINE_WAITING taskId={} stage=VIDEO_UNDERSTANDING "
                                    + "reason=vision_model_unavailable model={}",
                            taskId, visionClient.model());
                    return;
                }
                stateService.markVideoUnderstandingRunning(taskId);
                EngineTaskContext analysisContext = context;
                VideoUnderstandingResult result = retryExecutor.analysis(() -> analysisContext.cloudVisionEnabled()
                        ? visionClient.analyzeDetailed(Path.of(analysisContext.sceneManifestPath()), analysisContext.transcriptText(),
                            progress -> stateService.updateStageProgress(taskId,
                                    ProcessingStageType.VIDEO_UNDERSTANDING, progress))
                        : visionClient.analyzeWithoutAi(Path.of(analysisContext.sceneManifestPath()), analysisContext.transcriptText()));
                stateService.markVideoUnderstandingCompleted(taskId, result);
                segmentSemanticIndex.index(taskId, Path.of(result.analysisPath()));
                log.info("ENGINE_STAGE_COMPLETED taskId={} stage=VIDEO_UNDERSTANDING frameCount={}",
                        taskId, result.frames().size());
                context = stateService.context(taskId);
            } else {
                log.info("ENGINE_STAGE_SKIPPED taskId={} stage=VIDEO_UNDERSTANDING reason=already_completed",
                        taskId);
            }
            activeStage = "HIGHLIGHT_SELECTION";
            checkCancellation(taskId);
            if (!context.highlightSelectionCompleted()) {
                stateService.markHighlightSelectionRunning(taskId);
                HighlightSelectionResult result = highlightSelector.select(
                        Path.of(context.visualAnalysisPath()), context.durationSeconds(),
                        context.targetDurationSeconds(), context.editingScope());
                gameEvents.rebuild(taskId, Path.of(context.visualAnalysisPath()), Path.of(result.manifestPath()));
                stateService.markHighlightSelectionCompleted(taskId, result);
                log.info("ENGINE_STAGE_COMPLETED taskId={} stage=HIGHLIGHT_SELECTION clipCount={}",
                        taskId, result.clips().size());
                context = stateService.context(taskId);
            } else {
                log.info("ENGINE_STAGE_SKIPPED taskId={} stage=HIGHLIGHT_SELECTION reason=already_completed",
                        taskId);
            }
            if (gameEvents.list(taskId).isEmpty()) {
                gameEvents.rebuild(taskId, Path.of(context.visualAnalysisPath()),
                        Path.of(context.highlightManifestPath()));
            }
            activeStage = "SCRIPT_GENERATION";
            checkCancellation(taskId);
            if (!context.scriptGenerationCompleted()) {
                stateService.markScriptGenerationRunning(taskId);
                EngineTaskContext generationContext = context;
                GeneratedScript result = retryExecutor.generation(() -> generationContext.aiScriptEnabled()
                        ? scriptGenerator.generate(Path.of(generationContext.highlightManifestPath()),
                            generationContext.gameCategory(), generationContext.commentaryStyle(), generationContext.taskBrief(),
                            generationContext.transcriptText(), gameEvents.confirmedFacts(taskId))
                        : scriptGenerator.generateWithoutAi(Path.of(generationContext.highlightManifestPath())));
                stateService.markScriptGenerationCompleted(taskId, result);
                log.info("ENGINE_STAGE_COMPLETED taskId={} stage=SCRIPT_GENERATION segmentCount={}",
                        taskId, result.segments().size());
                context = stateService.context(taskId);
            } else {
                log.info("ENGINE_STAGE_SKIPPED taskId={} stage=SCRIPT_GENERATION reason=already_completed", taskId);
            }
            if (context.autoAssetsEnabled()) try {
                var assignment = storyboardAssets.autoAssignIfEmpty(taskId);
                log.info("STORYBOARD_AUTO_ASSETS taskId={} assigned={} warnings={}", taskId,
                        assignment.assignedCount(), assignment.warnings().size());
            } catch (Exception exception) {
                log.warn("STORYBOARD_AUTO_ASSETS_SKIPPED taskId={} reason={}", taskId, exception.getMessage());
            }
            if (context.storyboardReviewEnabled() && !context.storyboardApproved()) {
                stateService.markStoryboardReviewWaiting(taskId);
                log.info("ENGINE_WAITING taskId={} stage=STORYBOARD_REVIEW", taskId);
                return;
            }
            activeStage = "VOICE_GENERATION";
            checkCancellation(taskId);
            if (!context.voiceGenerationCompleted()) {
                if (context.aiVoiceEnabled() && !voiceGenerator.available()) {
                    String reason = "等待本地 Piper 配音引擎；请执行 .\\scripts\\setup-piper.ps1";
                    stateService.deferVoiceGeneration(taskId, reason);
                    log.warn("ENGINE_WAITING taskId={} stage=VOICE_GENERATION reason=piper_unavailable", taskId);
                    return;
                }
                stateService.markVoiceGenerationRunning(taskId);
                VoiceGenerationResult result = context.aiVoiceEnabled()
                        ? voiceGenerator.generateDetailed(Path.of(context.generatedScriptPath()),
                            progress -> stateService.updateStageProgress(taskId,
                                    ProcessingStageType.VOICE_GENERATION, progress))
                        : silentVoiceGenerator.generate(Path.of(context.generatedScriptPath()));
                stateService.markVoiceGenerationCompleted(taskId, result);
                log.info("ENGINE_STAGE_COMPLETED taskId={} stage=VOICE_GENERATION segmentCount={}",
                        taskId, result.segments().size());
                context = stateService.context(taskId);
            } else {
                log.info("ENGINE_STAGE_SKIPPED taskId={} stage=VOICE_GENERATION reason=already_completed", taskId);
            }
            activeStage = "TIMELINE_PLANNING";
            checkCancellation(taskId);
            if (!context.timelinePlanningCompleted()) {
                stateService.markTimelinePlanningRunning(taskId);
                TimelinePlanningResult result = timelinePlanner.plan(
                        Path.of(context.highlightManifestPath()), Path.of(context.generatedScriptPath()),
                        Path.of(context.voiceManifestPath()));
                stateService.markTimelinePlanningCompleted(taskId, result, !context.automaticGenerationEnabled());
                log.info("ENGINE_STAGE_COMPLETED taskId={} stage=TIMELINE_PLANNING segmentCount={} duration={}",
                        taskId, result.segments().size(), result.outputDurationSeconds());
                context = stateService.context(taskId);
            } else {
                log.info("ENGINE_STAGE_SKIPPED taskId={} stage=TIMELINE_PLANNING reason=already_completed", taskId);
            }
            if (!context.automaticGenerationEnabled()) {
                log.info("ENGINE_READY taskId={} mode=manual editableTimeline=true", taskId);
                return;
            }
            activeStage = "RENDERING";
            checkCancellation(taskId);
            if (!context.renderingCompleted()) {
                stateService.markRenderingRunning(taskId);
                var preset = effectPresetCatalog.require(context.commentaryStyle());
                var settings = new EffectSettingsRequest(preset.code(), null, true, true, true, null,
                        false, 0d, 1d, 1d, 0d, false);
                RenderResult result = videoRenderer.renderDetailed(sourcePath, Path.of(context.timelinePath()),
                        context.hasAudio(), preset, settings,
                        progress -> stateService.updateStageProgress(taskId,
                                ProcessingStageType.RENDERING, progress));
                stateService.markRenderingCompleted(taskId, result);
                log.info("ENGINE_STAGE_COMPLETED taskId={} stage=RENDERING output={} sizeBytes={}",
                        taskId, result.videoPath(), result.fileSizeBytes());
            } else {
                log.info("ENGINE_STAGE_SKIPPED taskId={} stage=RENDERING reason=already_completed", taskId);
            }
            log.info("ENGINE_COMPLETED taskId={}", taskId);
        } catch (CancellationException exception) {
            if (deletionRequested.contains(taskId)) {
                log.info("ENGINE_STOPPED_DELETED taskId={} stage={}", taskId, activeStage);
                return;
            }
            stateService.markCancelled(taskId, activeStage, "用户取消了任务");
            log.info("ENGINE_CANCELLED taskId={} stage={}", taskId, activeStage);
        } catch (Exception exception) {
            if (deletionRequested.contains(taskId)) {
                log.info("ENGINE_STOPPED_DELETED taskId={} stage={}", taskId, activeStage);
                return;
            }
            String reason = rootMessage(exception);
            log.error("ENGINE_FAILED taskId={} stage={} message={}",
                    taskId, activeStage, reason, exception);
            if ("SCENE_DETECTION".equals(activeStage)) {
                stateService.markSceneDetectionFailed(taskId, reason);
            } else if ("TRANSCRIPTION".equals(activeStage)) {
                stateService.markTranscriptionFailed(taskId, reason);
            } else if ("VIDEO_UNDERSTANDING".equals(activeStage)) {
                stateService.markVideoUnderstandingFailed(taskId, reason);
            } else if ("HIGHLIGHT_SELECTION".equals(activeStage)) {
                stateService.markHighlightSelectionFailed(taskId, reason);
            } else if ("SCRIPT_GENERATION".equals(activeStage)) {
                stateService.markScriptGenerationFailed(taskId, reason);
            } else if ("VOICE_GENERATION".equals(activeStage)) {
                stateService.markVoiceGenerationFailed(taskId, reason);
            } else if ("TIMELINE_PLANNING".equals(activeStage)) {
                stateService.markTimelinePlanningFailed(taskId, reason);
            } else if ("RENDERING".equals(activeStage)) {
                stateService.markRenderingFailed(taskId, reason);
            } else {
                stateService.markIngestionFailed(taskId, reason);
            }
        } finally {
            if (resourceLease != null) resourceLease.close();
            activeTasks.remove(taskId);
            deletionRequested.remove(taskId);
            MDC.remove("traceId");
        }
    }

    private void checkCancellation(UUID taskId) {
        TaskProcessRegistry.throwIfCancelled(taskId);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
