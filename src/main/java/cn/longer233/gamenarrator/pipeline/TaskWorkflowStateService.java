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

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
public class TaskWorkflowStateService {

    private final VideoTaskRepository repository;
    private final PipelineRunTracker runTracker;
    private final cn.longer233.gamenarrator.transcription.TerminologyCorrector terminologyCorrector;
    private final cn.longer233.gamenarrator.transcription.SpeakerDiarizationService speakerDiarization;
    private final ProjectArtifactRegistry artifactRegistry;
    private final TaskArtifactLocator artifactLocator;

    public TaskWorkflowStateService(VideoTaskRepository repository, PipelineRunTracker runTracker,
                                    cn.longer233.gamenarrator.transcription.TerminologyCorrector terminologyCorrector,
                                    cn.longer233.gamenarrator.transcription.SpeakerDiarizationService speakerDiarization,
                                    ProjectArtifactRegistry artifactRegistry,
                                    TaskArtifactLocator artifactLocator) {
        this.repository = repository;
        this.runTracker = runTracker;
        this.terminologyCorrector = terminologyCorrector;
        this.speakerDiarization = speakerDiarization;
        this.artifactRegistry = artifactRegistry;
        this.artifactLocator = artifactLocator;
    }

    @Transactional
    public EngineTaskContext context(UUID taskId) {
        VideoTask task = requireTask(taskId);
        return new EngineTaskContext(
                task.getSourceVideoPath(),
                completedStages(task),
                task.getAudioCodec() != null && !"none".equalsIgnoreCase(task.getAudioCodec()),
                artifactPath(taskId, "EXTRACTED_AUDIO"),
                artifactPath(taskId, "SCENE_MANIFEST"),
                task.getTranscriptText(),
                artifactPath(taskId, "VISION_ANALYSIS"),
                artifactPath(taskId, "HIGHLIGHT_MANIFEST"),
                artifactPath(taskId, "SCRIPT_MANIFEST"),
                artifactPath(taskId, "VOICE_MANIFEST"),
                artifactPath(taskId, "TIMELINE_MANIFEST"),
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

    private String artifactPath(UUID taskId, String artifactType) {
        return artifactLocator.latest(taskId, artifactType).map(java.nio.file.Path::toString).orElse(null);
    }

    private Set<ProcessingStageType> completedStages(VideoTask task) {
        EnumSet<ProcessingStageType> completed = EnumSet.noneOf(ProcessingStageType.class);
        for (ProcessingStageType stageType : ProcessingStageType.values()) {
            if (task.isStageCompleted(stageType)) completed.add(stageType);
        }
        return completed;
    }

    @Transactional
    public void markManualEditingReady(UUID taskId) {
        requireTask(taskId).readyForManualEditing();
    }

    @Transactional
    public void updateStageProgress(UUID taskId, ProcessingStageType stageType, int progress) {
        requireTask(taskId).updateStageProgress(stageType, progress);
        runTracker.progress(taskId, stageType.name(), progress);
    }

    @Transactional
    public void updateStageProgress(UUID taskId, ProcessingStageType stageType, StageProgressUpdate update) {
        requireTask(taskId).updateStageProgress(stageType, update.percent(), update.unit(), update.current(),
                update.total(), update.detail());
        runTracker.progress(taskId, stageType.name(), update.percent());
    }

    @Transactional
    public void markCancelled(UUID taskId, ProcessingStageType stageType, String reason) {
        requireTask(taskId).cancel(reason);
        runTracker.cancelled(taskId, stageType.name(), reason);
    }

    @Transactional
    public void markFailed(UUID taskId, ProcessingStageType stageType, String reason) {
        VideoTask task = requireTask(taskId);
        switch (stageType) {
            case VIDEO_INGESTION -> task.failIngestion(reason);
            case SCENE_DETECTION -> task.failSceneDetection(reason);
            case TRANSCRIPTION -> task.failTranscription(reason);
            case VIDEO_UNDERSTANDING -> task.failVideoUnderstanding(reason);
            case HIGHLIGHT_SELECTION -> task.failHighlightSelection(reason);
            case SCRIPT_GENERATION -> task.failScriptGeneration(reason);
            case VOICE_GENERATION -> task.failVoiceGeneration(reason);
            case TIMELINE_PLANNING -> task.failTimelinePlanning(reason);
            case RENDERING -> task.failRendering(reason);
        }
        runTracker.failed(taskId, stageType.name(), reason);
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
        artifactRegistry.record(taskId, "SCENE_MANIFEST", result.sceneManifestPath(), "application/json", false);
        artifactRegistry.record(taskId, "EXTRACTED_AUDIO", result.extractedAudioPath(), "audio/wav", false);
        runTracker.completed(taskId, "SCENE_DETECTION", java.util.Map.of("sceneCount", result.scenes().size()));
    }

    @Transactional
    public void markTranscriptionRunning(UUID taskId) {
        requireTask(taskId).startTranscription();
        runTracker.running(taskId, "TRANSCRIPTION");
    }

    @Transactional
    public void markTranscriptionCompleted(UUID taskId, TranscriptionResult result) {
        VideoTask task = requireTask(taskId);
        TranscriptionResult corrected = terminologyCorrector.correct(result, task.getTerminologyGlossary());
        task.completeTranscription(corrected.text(), corrected.textPath(), corrected.subtitlePath(), corrected.detailJsonPath());
        artifactRegistry.record(taskId, "TRANSCRIPT_TEXT", corrected.textPath(), "text/plain", false);
        artifactRegistry.record(taskId, "TRANSCRIPT_SUBTITLE", corrected.subtitlePath(), "application/x-subrip", false);
        artifactRegistry.record(taskId, "TRANSCRIPT_DETAIL", corrected.detailJsonPath(), "application/json", false);
        java.nio.file.Path speakerSegments = speakerDiarization.analyze(corrected);
        artifactRegistry.record(taskId, "SPEAKER_SEGMENTS",
                speakerSegments == null ? null : speakerSegments.toString(), "application/json", false);
        runTracker.completed(taskId, "TRANSCRIPTION", java.util.Map.of("characterCount", corrected.text().length()));
    }

    /** Applies an optional enhancement without moving the overall task into PROCESSING or FAILED. */
    @Transactional
    public void applyTranscriptionEnhancement(UUID taskId, TranscriptionResult result) {
        VideoTask task = requireTask(taskId);
        TranscriptionResult corrected = terminologyCorrector.correct(result, task.getTerminologyGlossary());
        task.completeTranscription(corrected.text(), corrected.textPath(), corrected.subtitlePath(), corrected.detailJsonPath());
        artifactRegistry.record(taskId, "TRANSCRIPT_TEXT", corrected.textPath(), "text/plain", false);
        artifactRegistry.record(taskId, "TRANSCRIPT_SUBTITLE", corrected.subtitlePath(), "application/x-subrip", false);
        artifactRegistry.record(taskId, "TRANSCRIPT_DETAIL", corrected.detailJsonPath(), "application/json", false);
        java.nio.file.Path speakerSegments = speakerDiarization.analyze(corrected);
        artifactRegistry.record(taskId, "SPEAKER_SEGMENTS",
                speakerSegments == null ? null : speakerSegments.toString(), "application/json", false);
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
        artifactRegistry.record(taskId, "VISION_ANALYSIS", result.analysisPath(), "application/json", false);
        runTracker.completed(taskId, "VIDEO_UNDERSTANDING", java.util.Map.of("frameCount", result.frames().size()));
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
        artifactRegistry.record(taskId, "HIGHLIGHT_MANIFEST", result.manifestPath(), "application/json", false);
        runTracker.completed(taskId, "HIGHLIGHT_SELECTION", java.util.Map.of("clipCount", result.clips().size()));
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
        artifactRegistry.record(taskId, "SCRIPT_MANIFEST", result.scriptPath(), "application/json", false);
        runTracker.completed(taskId, "SCRIPT_GENERATION", java.util.Map.of("segmentCount", result.segments().size()));
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
        artifactRegistry.record(taskId, "VOICE_MANIFEST", result.manifestPath(), "application/json", false);
        runTracker.completed(taskId, "VOICE_GENERATION", java.util.Map.of("segmentCount", result.segments().size()));
    }

    @Transactional
    public void deferVoiceGeneration(UUID taskId, String reason) {
        requireTask(taskId).deferVoiceGeneration(reason);
        runTracker.waiting(taskId, "VOICE_GENERATION", reason);
    }

    @Transactional
    public void markTimelinePlanningRunning(UUID taskId) {
        requireTask(taskId).startTimelinePlanning();
        runTracker.running(taskId, "TIMELINE_PLANNING");
    }

    @Transactional
    public void markTimelinePlanningCompleted(UUID taskId, TimelinePlanningResult result) {
        markTimelinePlanningCompleted(taskId, result, false);
    }

    @Transactional
    public void markTimelinePlanningCompleted(UUID taskId, TimelinePlanningResult result, boolean manualEditingReady) {
        VideoTask task = requireTask(taskId);
        task.completeTimelinePlanning(
                result.timelinePath(), result.outputDurationSeconds(), result.overflowCount());
        if (manualEditingReady) task.readyForManualEditing();
        artifactRegistry.record(taskId, "TIMELINE_MANIFEST", result.timelinePath(), "application/json", false);
        runTracker.completed(taskId, "TIMELINE_PLANNING", java.util.Map.of(
                "outputDurationSeconds", result.outputDurationSeconds(), "overflowCount", result.overflowCount()));
    }

    @Transactional
    public void markRenderingRunning(UUID taskId) {
        requireTask(taskId).startRendering();
        runTracker.running(taskId, "RENDERING");
    }

    @Transactional
    public void markRenderingCompleted(UUID taskId, RenderResult result) {
        requireTask(taskId).completeRendering(result.videoPath(), result.subtitlePath(), result.fileSizeBytes());
        artifactRegistry.record(taskId, "RENDERED_VIDEO", result.videoPath(), "video/mp4", false);
        artifactRegistry.record(taskId, "GENERATED_SUBTITLE", result.subtitlePath(), "application/x-subrip", false);
        runTracker.completed(taskId, "RENDERING", java.util.Map.of("fileSizeBytes", result.fileSizeBytes()));
    }

    private VideoTask requireTask(UUID taskId) {
        return repository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("异步任务不存在：" + taskId));
    }
}
