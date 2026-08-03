package cn.longer233.gamenarrator.pipeline;

import cn.longer233.gamenarrator.media.MediaMetadata;
import cn.longer233.gamenarrator.media.MediaPreparationResult;
import cn.longer233.gamenarrator.task.domain.ProcessingStageType;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import cn.longer233.gamenarrator.transcription.TranscriptionResult;
import cn.longer233.gamenarrator.vision.VideoUnderstandingResult;
import cn.longer233.gamenarrator.highlight.HighlightSelectionResult;
import cn.longer233.gamenarrator.script.GeneratedScript;
import cn.longer233.gamenarrator.voice.VoiceGenerationResult;
import cn.longer233.gamenarrator.timeline.TimelinePlanningResult;
import cn.longer233.gamenarrator.render.RenderResult;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TaskWorkflowStateService {

    private final VideoTaskRepository repository;
    private final PipelineRunTracker runTracker;

    public TaskWorkflowStateService(VideoTaskRepository repository, PipelineRunTracker runTracker) {
        this.repository = repository;
        this.runTracker = runTracker;
    }

    @Transactional
    public EngineTaskContext context(UUID taskId) {
        VideoTask task = requireTask(taskId);
        return new EngineTaskContext(
                task.getSourceVideoPath(),
                task.isStageCompleted(ProcessingStageType.VIDEO_INGESTION),
                task.isStageCompleted(ProcessingStageType.SCENE_DETECTION),
                task.isStageCompleted(ProcessingStageType.TRANSCRIPTION),
                task.isStageCompleted(ProcessingStageType.VIDEO_UNDERSTANDING),
                task.isStageCompleted(ProcessingStageType.HIGHLIGHT_SELECTION),
                task.isStageCompleted(ProcessingStageType.SCRIPT_GENERATION),
                task.isStageCompleted(ProcessingStageType.VOICE_GENERATION),
                task.isStageCompleted(ProcessingStageType.TIMELINE_PLANNING),
                task.isStageCompleted(ProcessingStageType.RENDERING),
                task.getAudioCodec() != null && !"none".equalsIgnoreCase(task.getAudioCodec()),
                task.getExtractedAudioPath(),
                task.getSceneManifestPath(),
                task.getTranscriptText(),
                task.getVisualAnalysisPath(),
                task.getHighlightManifestPath(),
                task.getGeneratedScriptPath(),
                task.getVoiceManifestPath(),
                task.getTimelinePath(),
                task.getDurationSeconds(),
                task.getTargetDurationSeconds(),
                task.getEditingScope().name(),
                task.getGameCategory(),
                task.getCommentaryStyle().name(),
                task.getTaskBrief(),
                task.isStoryboardReviewEnabled(),
                task.isStoryboardApproved(),
                task.isCloudVisionEnabled(),
                task.isAiScriptEnabled(),
                task.isAiVoiceEnabled(),
                task.isAutoAssetsEnabled(),
                task.isAutomaticGenerationEnabled()
        );
    }

    @Transactional
    public void markManualEditingReady(UUID taskId) {
        requireTask(taskId).readyForManualEditing();
    }

    @Transactional
    public void updateStageProgress(UUID taskId, ProcessingStageType stageType, int progress) {
        requireTask(taskId).updateStageProgress(stageType, progress);
    }

    @Transactional
    public void markIngestionRunning(UUID taskId) {
        requireTask(taskId).startIngestion();
        runTracker.running(taskId, "VIDEO_INGESTION");
    }

    @Transactional
    public void markIngestionCompleted(UUID taskId, MediaMetadata metadata) {
        requireTask(taskId).completeIngestion(
                metadata.durationSeconds(),
                metadata.width(),
                metadata.height(),
                metadata.framesPerSecond(),
                metadata.videoCodec(),
                metadata.audioCodec()
        );
        runTracker.completed(taskId, "VIDEO_INGESTION", java.util.Map.of("durationSeconds", metadata.durationSeconds()));
    }

    @Transactional
    public void markIngestionFailed(UUID taskId, String reason) {
        requireTask(taskId).failIngestion(reason);
        runTracker.failed(taskId, "VIDEO_INGESTION", reason);
    }

    @Transactional
    public void markSceneDetectionRunning(UUID taskId) {
        requireTask(taskId).startSceneDetection();
        runTracker.running(taskId, "SCENE_DETECTION");
    }

    @Transactional
    public void markSceneDetectionCompleted(UUID taskId, MediaPreparationResult result) {
        requireTask(taskId).completeSceneDetection(
                result.extractedAudioPath(),
                result.sceneManifestPath(),
                result.scenes().size()
        );
        runTracker.completed(taskId, "SCENE_DETECTION", java.util.Map.of("sceneCount", result.scenes().size()));
    }

    @Transactional
    public void markSceneDetectionFailed(UUID taskId, String reason) {
        requireTask(taskId).failSceneDetection(reason);
        runTracker.failed(taskId, "SCENE_DETECTION", reason);
    }

    @Transactional
    public void markTranscriptionRunning(UUID taskId) {
        requireTask(taskId).startTranscription();
        runTracker.running(taskId, "TRANSCRIPTION");
    }

    @Transactional
    public void markTranscriptionCompleted(UUID taskId, TranscriptionResult result) {
        requireTask(taskId).completeTranscription(
                result.text(), result.textPath(), result.subtitlePath(), result.detailJsonPath());
        runTracker.completed(taskId, "TRANSCRIPTION", java.util.Map.of("characterCount", result.text().length()));
    }

    @Transactional
    public void markTranscriptionFailed(UUID taskId, String reason) {
        requireTask(taskId).failTranscription(reason);
        runTracker.failed(taskId, "TRANSCRIPTION", reason);
    }

    @Transactional
    public void markVideoUnderstandingRunning(UUID taskId) {
        requireTask(taskId).startVideoUnderstanding();
        runTracker.running(taskId, "VIDEO_UNDERSTANDING");
    }

    @Transactional
    public void markVideoUnderstandingCompleted(UUID taskId, VideoUnderstandingResult result) {
        requireTask(taskId).completeVideoUnderstanding(
                result.summary(), result.analysisPath(), result.frames().size());
        runTracker.completed(taskId, "VIDEO_UNDERSTANDING", java.util.Map.of("frameCount", result.frames().size()));
    }

    @Transactional
    public void markVideoUnderstandingFailed(UUID taskId, String reason) {
        requireTask(taskId).failVideoUnderstanding(reason);
        runTracker.failed(taskId, "VIDEO_UNDERSTANDING", reason);
    }

    @Transactional
    public void deferVideoUnderstanding(UUID taskId, String reason) {
        requireTask(taskId).deferVideoUnderstanding(reason);
        runTracker.waiting(taskId, "VIDEO_UNDERSTANDING", reason);
    }

    @Transactional
    public void markHighlightSelectionRunning(UUID taskId) {
        requireTask(taskId).startHighlightSelection();
        runTracker.running(taskId, "HIGHLIGHT_SELECTION");
    }

    @Transactional
    public void markHighlightSelectionCompleted(UUID taskId, HighlightSelectionResult result) {
        requireTask(taskId).completeHighlightSelection(result.summary(), result.manifestPath(), result.clips().size());
        runTracker.completed(taskId, "HIGHLIGHT_SELECTION", java.util.Map.of("clipCount", result.clips().size()));
    }

    @Transactional
    public void markHighlightSelectionFailed(UUID taskId, String reason) {
        requireTask(taskId).failHighlightSelection(reason);
        runTracker.failed(taskId, "HIGHLIGHT_SELECTION", reason);
    }

    @Transactional
    public void markScriptGenerationRunning(UUID taskId) {
        requireTask(taskId).startScriptGeneration();
        runTracker.running(taskId, "SCRIPT_GENERATION");
    }

    @Transactional
    public void markScriptGenerationCompleted(UUID taskId, GeneratedScript result) {
        requireTask(taskId).completeScriptGeneration(result.title(), result.synopsis(),
                result.fullNarration(), result.scriptPath(), result.segments().size());
        runTracker.completed(taskId, "SCRIPT_GENERATION", java.util.Map.of("segmentCount", result.segments().size()));
    }

    @Transactional
    public void markScriptGenerationFailed(UUID taskId, String reason) {
        requireTask(taskId).failScriptGeneration(reason);
        runTracker.failed(taskId, "SCRIPT_GENERATION", reason);
    }

    @Transactional
    public void markStoryboardReviewWaiting(UUID taskId) {
        requireTask(taskId).awaitStoryboardReview();
    }

    @Transactional
    public void markVoiceGenerationRunning(UUID taskId) {
        requireTask(taskId).startVoiceGeneration();
        runTracker.running(taskId, "VOICE_GENERATION");
    }

    @Transactional
    public void markVoiceGenerationCompleted(UUID taskId, VoiceGenerationResult result) {
        requireTask(taskId).completeVoiceGeneration(result.manifestPath(), result.segments().size());
        runTracker.completed(taskId, "VOICE_GENERATION", java.util.Map.of("segmentCount", result.segments().size()));
    }

    @Transactional
    public void deferVoiceGeneration(UUID taskId, String reason) {
        requireTask(taskId).deferVoiceGeneration(reason);
        runTracker.waiting(taskId, "VOICE_GENERATION", reason);
    }

    @Transactional
    public void markVoiceGenerationFailed(UUID taskId, String reason) {
        requireTask(taskId).failVoiceGeneration(reason);
        runTracker.failed(taskId, "VOICE_GENERATION", reason);
    }

    @Transactional
    public void markTimelinePlanningRunning(UUID taskId) {
        requireTask(taskId).startTimelinePlanning();
        runTracker.running(taskId, "TIMELINE_PLANNING");
    }

    @Transactional
    public void markTimelinePlanningCompleted(UUID taskId, TimelinePlanningResult result) {
        requireTask(taskId).completeTimelinePlanning(
                result.timelinePath(), result.outputDurationSeconds(), result.overflowCount());
        runTracker.completed(taskId, "TIMELINE_PLANNING", java.util.Map.of(
                "outputDurationSeconds", result.outputDurationSeconds(), "overflowCount", result.overflowCount()));
    }

    @Transactional
    public void markTimelinePlanningFailed(UUID taskId, String reason) {
        requireTask(taskId).failTimelinePlanning(reason);
        runTracker.failed(taskId, "TIMELINE_PLANNING", reason);
    }

    @Transactional
    public void markRenderingRunning(UUID taskId) {
        requireTask(taskId).startRendering();
        runTracker.running(taskId, "RENDERING");
    }

    @Transactional
    public void markRenderingCompleted(UUID taskId, RenderResult result) {
        requireTask(taskId).completeRendering(result.videoPath(), result.subtitlePath(), result.fileSizeBytes());
        runTracker.completed(taskId, "RENDERING", java.util.Map.of("fileSizeBytes", result.fileSizeBytes()));
    }

    @Transactional
    public void markRenderingFailed(UUID taskId, String reason) {
        requireTask(taskId).failRendering(reason);
        runTracker.failed(taskId, "RENDERING", reason);
    }

    private VideoTask requireTask(UUID taskId) {
        return repository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("异步任务不存在：" + taskId));
    }
}
