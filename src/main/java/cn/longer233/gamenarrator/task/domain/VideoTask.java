package cn.longer233.gamenarrator.task.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "video_tasks")
@SQLRestriction("deleted_at IS NULL")
public class VideoTask {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 40)
    private String gameCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CommentaryStyle commentaryStyle;

    @Column(nullable = false)
    private int targetDurationSeconds;

    @Column(name = "processing_priority", nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EditingScope editingScope = EditingScope.FULL_VIDEO;

    @Column(nullable = false, length = 500)
    private String taskBrief;

    @Column(length = 4000)
    private String terminologyGlossary;

    @Column(nullable = false, length = 500)
    private String sourceVideoPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    private Double durationSeconds;
    private Integer videoWidth;
    private Integer videoHeight;
    private Double framesPerSecond;

    @Column(length = 80)
    private String videoCodec;

    @Column(length = 80)
    private String audioCodec;

    @Column(length = 1000)
    private String failureReason;

    @Column(length = 500)
    private String extractedAudioPath;

    @Column(length = 500)
    private String sceneManifestPath;

    private Integer detectedSceneCount;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String transcriptText;

    @Column(length = 500)
    private String transcriptTextPath;

    @Column(length = 500)
    private String subtitlePath;

    @Column(length = 500)
    private String transcriptJsonPath;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String visualSummary;

    @Column(length = 500)
    private String visualAnalysisPath;

    private Integer analyzedFrameCount;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String highlightSummary;

    @Column(length = 500)
    private String highlightManifestPath;

    private Integer selectedHighlightCount;

    @Column(length = 200)
    private String generatedTitle;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String scriptSynopsis;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String generatedNarration;

    @Column(length = 500)
    private String generatedScriptPath;

    private Integer generatedScriptSegmentCount;

    @Column(length = 500)
    private String voiceManifestPath;

    private Integer generatedVoiceSegmentCount;

    @Column(length = 500)
    private String timelinePath;

    private Double plannedOutputDurationSeconds;
    private Integer voiceOverflowCount;

    @Column(length = 500)
    private String renderedVideoPath;

    @Column(length = 500)
    private String generatedSubtitlePath;

    private Long renderedFileSizeBytes;

    @Column(nullable = false)
    private boolean storyboardReviewEnabled;

    @Column(nullable = false)
    private boolean storyboardApproved;

    @Column(nullable = false) private boolean cloudVisionEnabled = true;
    @Column(nullable = false) private boolean aiScriptEnabled = true;
    @Column(nullable = false) private boolean aiVoiceEnabled = true;
    @Column(nullable = false) private boolean autoAssetsEnabled = true;
    @Column(nullable = false) private boolean automaticGenerationEnabled = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    private Instant deletedAt;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNumber ASC")
    private List<ProcessingStage> stages = new ArrayList<>();

    protected VideoTask() {
    }

    public VideoTask(String name, String gameCategory, CommentaryStyle commentaryStyle,
                     int targetDurationSeconds, String taskBrief, String sourceVideoPath) {
        this(name, gameCategory, commentaryStyle, targetDurationSeconds, taskBrief, sourceVideoPath, false);
    }

    public VideoTask(String name, String gameCategory, CommentaryStyle commentaryStyle,
                     int targetDurationSeconds, String taskBrief, String sourceVideoPath,
                     boolean storyboardReviewEnabled) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.gameCategory = gameCategory;
        this.commentaryStyle = commentaryStyle;
        this.targetDurationSeconds = targetDurationSeconds;
        this.taskBrief = taskBrief;
        this.sourceVideoPath = sourceVideoPath;
        this.status = TaskStatus.READY;
        this.storyboardReviewEnabled = storyboardReviewEnabled;
        this.storyboardApproved = !storyboardReviewEnabled;
        this.createdAt = Instant.now();
        int sequence = 1;
        for (ProcessingStageType type : ProcessingStageType.values()) {
            stages.add(new ProcessingStage(this, type, sequence++));
        }
    }

    public void configureAiOptions(boolean automaticGenerationEnabled, boolean cloudVisionEnabled,
                                   boolean aiScriptEnabled, boolean aiVoiceEnabled, boolean autoAssetsEnabled) {
        this.automaticGenerationEnabled = automaticGenerationEnabled;
        this.cloudVisionEnabled = cloudVisionEnabled;
        this.aiScriptEnabled = aiScriptEnabled;
        this.aiVoiceEnabled = aiVoiceEnabled;
        this.autoAssetsEnabled = autoAssetsEnabled;
    }

    public void configureEditingScope(EditingScope editingScope) {
        this.editingScope = editingScope == null ? EditingScope.FULL_VIDEO : editingScope;
    }

    public void configureTerminologyGlossary(String glossary) {
        this.terminologyGlossary = glossary == null || glossary.isBlank() ? null : glossary.strip();
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getProjectId() { return projectId; }
    public void assignOwnership(UUID ownerId) { this.ownerId = ownerId; }
    public String getName() { return name; }
    public String getGameCategory() { return gameCategory; }
    public CommentaryStyle getCommentaryStyle() { return commentaryStyle; }
    public int getTargetDurationSeconds() { return targetDurationSeconds; }
    public int getPriority() { return priority; }
    public EditingScope getEditingScope() { return editingScope; }
    public String getTaskBrief() { return taskBrief; }
    public String getTerminologyGlossary() { return terminologyGlossary; }
    public String getSourceVideoPath() { return sourceVideoPath; }
    public TaskStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void moveToTrash() { this.deletedAt = Instant.now(); }
    public List<ProcessingStage> getStages() { return List.copyOf(stages); }
    public Double getDurationSeconds() { return durationSeconds; }
    public Integer getVideoWidth() { return videoWidth; }
    public Integer getVideoHeight() { return videoHeight; }
    public Double getFramesPerSecond() { return framesPerSecond; }
    public String getVideoCodec() { return videoCodec; }
    public String getAudioCodec() { return audioCodec; }
    public String getFailureReason() { return failureReason; }
    public String getExtractedAudioPath() { return extractedAudioPath; }
    public String getSceneManifestPath() { return sceneManifestPath; }
    public Integer getDetectedSceneCount() { return detectedSceneCount; }
    public String getTranscriptText() { return transcriptText; }
    public String getTranscriptTextPath() { return transcriptTextPath; }
    public String getSubtitlePath() { return subtitlePath; }
    public String getTranscriptJsonPath() { return transcriptJsonPath; }
    public String getVisualSummary() { return visualSummary; }
    public String getVisualAnalysisPath() { return visualAnalysisPath; }
    public Integer getAnalyzedFrameCount() { return analyzedFrameCount; }
    public String getHighlightSummary() { return highlightSummary; }
    public String getHighlightManifestPath() { return highlightManifestPath; }
    public Integer getSelectedHighlightCount() { return selectedHighlightCount; }
    public String getGeneratedTitle() { return generatedTitle; }
    public String getScriptSynopsis() { return scriptSynopsis; }
    public String getGeneratedNarration() { return generatedNarration; }
    public String getGeneratedScriptPath() { return generatedScriptPath; }
    public Integer getGeneratedScriptSegmentCount() { return generatedScriptSegmentCount; }
    public String getVoiceManifestPath() { return voiceManifestPath; }
    public Integer getGeneratedVoiceSegmentCount() { return generatedVoiceSegmentCount; }
    public String getTimelinePath() { return timelinePath; }
    public Double getPlannedOutputDurationSeconds() { return plannedOutputDurationSeconds; }
    public Integer getVoiceOverflowCount() { return voiceOverflowCount; }
    public String getRenderedVideoPath() { return renderedVideoPath; }
    public String getGeneratedSubtitlePath() { return generatedSubtitlePath; }
    public Long getRenderedFileSizeBytes() { return renderedFileSizeBytes; }
    public boolean isStoryboardReviewEnabled() { return storyboardReviewEnabled; }
    public boolean isStoryboardApproved() { return storyboardApproved; }
    public boolean isCloudVisionEnabled() { return cloudVisionEnabled; }
    public boolean isAiScriptEnabled() { return aiScriptEnabled; }
    public boolean isAiVoiceEnabled() { return aiVoiceEnabled; }
    public boolean isAutoAssetsEnabled() { return autoAssetsEnabled; }
    public boolean isAutomaticGenerationEnabled() { return automaticGenerationEnabled; }
    public void readyForManualEditing() { this.status = TaskStatus.READY; this.failureReason = null; }

    public void awaitStoryboardReview() {
        if (storyboardReviewEnabled && !storyboardApproved) {
            this.status = TaskStatus.WAITING_REVIEW;
            this.failureReason = null;
        }
    }

    public void approveStoryboard() {
        if (!isStageCompleted(ProcessingStageType.SCRIPT_GENERATION)) {
            throw new IllegalStateException("AI 尚未生成分镜和文案");
        }
        this.storyboardApproved = true;
        this.status = TaskStatus.READY;
        this.failureReason = null;
    }

    public void rename(String newName) {
        String normalized = newName == null ? "" : newName.trim();
        if (normalized.isBlank() || normalized.length() > 120) {
            throw new IllegalArgumentException("任务名称长度必须为 1 到 120 个字符");
        }
        this.name = normalized;
    }

    public void changePriority(int priority) {
        if (priority < -100 || priority > 100) {
            throw new IllegalArgumentException("任务优先级必须在 -100 到 100 之间");
        }
        this.priority = priority;
    }

    public void startIngestion() {
        this.status = TaskStatus.PROCESSING;
        stage(ProcessingStageType.VIDEO_INGESTION).start();
    }

    public void completeIngestion(
            double durationSeconds,
            int videoWidth,
            int videoHeight,
            double framesPerSecond,
            String videoCodec,
            String audioCodec
    ) {
        this.durationSeconds = durationSeconds;
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.framesPerSecond = framesPerSecond;
        this.videoCodec = videoCodec;
        this.audioCodec = audioCodec;
        stage(ProcessingStageType.VIDEO_INGESTION).complete();
    }

    public void failIngestion(String reason) {
        this.status = TaskStatus.FAILED;
        this.failureReason = safeFailure(reason);
        stage(ProcessingStageType.VIDEO_INGESTION).fail(this.failureReason);
    }

    public boolean isStageCompleted(ProcessingStageType type) {
        return stage(type).getStatus() == StageStatus.COMPLETED;
    }

    public void updateStageProgress(ProcessingStageType type, int progress) {
        stage(type).updateProgress(progress);
    }

    public void prepareRetry() {
        if (status == TaskStatus.CANCELLED) {
            ProcessingStage cancelledStage = stages.stream()
                    .filter(item -> item.getStatus() == StageStatus.PENDING && item.getErrorMessage() != null)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Cancelled task has no resumable stage"));
            cancelledStage.prepareResume();
            this.status = TaskStatus.READY;
            this.failureReason = null;
            return;
        }
        if (status != TaskStatus.FAILED) {
            throw new IllegalStateException("Only a failed or cancelled task can be retried");
        }
        ProcessingStage failedStage = stages.stream()
                .filter(item -> item.getStatus() == StageStatus.FAILED)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Failed task has no failed stage"));
        failedStage.prepareRetry();
        this.status = TaskStatus.READY;
        this.failureReason = null;
    }

    public ProcessingStageType retryStage() {
        if (status == TaskStatus.CANCELLED) {
            return stages.stream()
                    .filter(item -> item.getStatus() == StageStatus.PENDING && item.getErrorMessage() != null)
                    .map(ProcessingStage::getStageType).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Cancelled task has no resumable stage"));
        }
        if (status != TaskStatus.FAILED) {
            throw new IllegalStateException("Only a failed or cancelled task can be retried");
        }
        return stages.stream().filter(item -> item.getStatus() == StageStatus.FAILED)
                .map(ProcessingStage::getStageType).findFirst()
                .orElseThrow(() -> new IllegalStateException("Failed task has no failed stage"));
    }

    public void startSceneDetection() {
        this.status = TaskStatus.PROCESSING;
        this.failureReason = null;
        stage(ProcessingStageType.SCENE_DETECTION).start();
    }

    public void completeSceneDetection(
            String extractedAudioPath,
            String sceneManifestPath,
            int detectedSceneCount
    ) {
        this.extractedAudioPath = extractedAudioPath;
        this.sceneManifestPath = sceneManifestPath;
        this.detectedSceneCount = detectedSceneCount;
        stage(ProcessingStageType.SCENE_DETECTION).complete();
    }

    public void failSceneDetection(String reason) {
        this.status = TaskStatus.FAILED;
        this.failureReason = safeFailure(reason);
        stage(ProcessingStageType.SCENE_DETECTION).fail(this.failureReason);
    }

    public void startTranscription() {
        this.status = TaskStatus.PROCESSING;
        this.failureReason = null;
        stage(ProcessingStageType.TRANSCRIPTION).start();
    }

    public void completeTranscription(
            String text,
            String textPath,
            String subtitlePath,
            String jsonPath
    ) {
        this.transcriptText = text;
        this.transcriptTextPath = textPath;
        this.subtitlePath = subtitlePath;
        this.transcriptJsonPath = jsonPath;
        stage(ProcessingStageType.TRANSCRIPTION).complete();
    }

    public void failTranscription(String reason) {
        this.status = TaskStatus.FAILED;
        this.failureReason = safeFailure(reason);
        stage(ProcessingStageType.TRANSCRIPTION).fail(this.failureReason);
    }

    public void startVideoUnderstanding() {
        this.status = TaskStatus.PROCESSING;
        this.failureReason = null;
        stage(ProcessingStageType.VIDEO_UNDERSTANDING).start();
    }

    public void completeVideoUnderstanding(String summary, String analysisPath, int frameCount) {
        this.visualSummary = summary;
        this.visualAnalysisPath = analysisPath;
        this.analyzedFrameCount = frameCount;
        stage(ProcessingStageType.VIDEO_UNDERSTANDING).complete();
    }

    public void failVideoUnderstanding(String reason) {
        this.status = TaskStatus.FAILED;
        this.failureReason = safeFailure(reason);
        stage(ProcessingStageType.VIDEO_UNDERSTANDING).fail(this.failureReason);
    }

    public void deferVideoUnderstanding(String reason) {
        this.status = TaskStatus.PROCESSING;
        this.failureReason = null;
        stage(ProcessingStageType.VIDEO_UNDERSTANDING).defer(reason);
    }

    public boolean canDeferFailedVideoUnderstanding() {
        return status == TaskStatus.FAILED
                && isStageCompleted(ProcessingStageType.TRANSCRIPTION)
                && stage(ProcessingStageType.VIDEO_UNDERSTANDING).getStatus() == StageStatus.FAILED;
    }

    public void startHighlightSelection() {
        this.status = TaskStatus.PROCESSING;
        this.failureReason = null;
        stage(ProcessingStageType.HIGHLIGHT_SELECTION).start();
    }

    public void completeHighlightSelection(String summary, String manifestPath, int clipCount) {
        this.highlightSummary = summary;
        this.highlightManifestPath = manifestPath;
        this.selectedHighlightCount = clipCount;
        stage(ProcessingStageType.HIGHLIGHT_SELECTION).complete();
    }

    public void failHighlightSelection(String reason) {
        this.status = TaskStatus.FAILED;
        this.failureReason = safeFailure(reason);
        stage(ProcessingStageType.HIGHLIGHT_SELECTION).fail(this.failureReason);
    }

    public void startScriptGeneration() {
        this.status = TaskStatus.PROCESSING;
        this.failureReason = null;
        stage(ProcessingStageType.SCRIPT_GENERATION).start();
    }

    public void completeScriptGeneration(String title, String synopsis, String narration,
                                         String scriptPath, int segmentCount) {
        this.generatedTitle = title;
        this.scriptSynopsis = synopsis;
        this.generatedNarration = narration;
        this.generatedScriptPath = scriptPath;
        this.generatedScriptSegmentCount = segmentCount;
        stage(ProcessingStageType.SCRIPT_GENERATION).complete();
    }

    public void applyScriptRevision(String title, String synopsis, String narration,
                                    String scriptPath, int segmentCount) {
        this.generatedTitle = title;
        this.scriptSynopsis = synopsis;
        this.generatedNarration = narration;
        this.generatedScriptPath = scriptPath;
        this.generatedScriptSegmentCount = segmentCount;
        stage(ProcessingStageType.SCRIPT_GENERATION).complete();
        invalidateAfterScript();
    }

    public void invalidateAfterConfirmedEventChange() {
        this.generatedTitle = null;
        this.scriptSynopsis = null;
        this.generatedNarration = null;
        this.generatedScriptPath = null;
        this.generatedScriptSegmentCount = null;
        this.voiceManifestPath = null;
        this.generatedVoiceSegmentCount = null;
        this.timelinePath = null;
        this.plannedOutputDurationSeconds = null;
        this.voiceOverflowCount = null;
        this.renderedVideoPath = null;
        this.generatedSubtitlePath = null;
        this.renderedFileSizeBytes = null;
        stage(ProcessingStageType.SCRIPT_GENERATION).reset();
        stage(ProcessingStageType.VOICE_GENERATION).reset();
        stage(ProcessingStageType.TIMELINE_PLANNING).reset();
        stage(ProcessingStageType.RENDERING).reset();
        this.status = TaskStatus.READY;
        this.failureReason = null;
    }

    public boolean isAwaitingScriptRegeneration() {
        StageStatus scriptStatus = stage(ProcessingStageType.SCRIPT_GENERATION).getStatus();
        return this.generatedScriptPath == null
                && (scriptStatus == StageStatus.PENDING || scriptStatus == StageStatus.RUNNING);
    }

    public void failScriptGeneration(String reason) {
        this.status = TaskStatus.FAILED;
        this.failureReason = safeFailure(reason);
        stage(ProcessingStageType.SCRIPT_GENERATION).fail(this.failureReason);
    }

    public void startVoiceGeneration() {
        this.status = TaskStatus.PROCESSING;
        this.failureReason = null;
        stage(ProcessingStageType.VOICE_GENERATION).start();
    }

    public void completeVoiceGeneration(String manifestPath, int segmentCount) {
        this.voiceManifestPath = manifestPath;
        this.generatedVoiceSegmentCount = segmentCount;
        stage(ProcessingStageType.VOICE_GENERATION).complete();
    }

    public void applyVoiceRevision(String manifestPath, int segmentCount) {
        this.voiceManifestPath = manifestPath;
        this.generatedVoiceSegmentCount = segmentCount;
        stage(ProcessingStageType.VOICE_GENERATION).complete();
        this.timelinePath = null;
        this.plannedOutputDurationSeconds = null;
        this.voiceOverflowCount = null;
        this.renderedVideoPath = null;
        this.generatedSubtitlePath = null;
        this.renderedFileSizeBytes = null;
        stage(ProcessingStageType.TIMELINE_PLANNING).reset();
        stage(ProcessingStageType.RENDERING).reset();
        this.status = storyboardReviewEnabled && !storyboardApproved
                ? TaskStatus.WAITING_REVIEW : TaskStatus.READY;
        this.failureReason = null;
    }

    public void applyLocalizedVoiceRevision(String manifestPath, int segmentCount, String revisedTimelinePath,
            double outputDuration, int overflowCount) {
        this.voiceManifestPath = manifestPath;
        this.generatedVoiceSegmentCount = segmentCount;
        stage(ProcessingStageType.VOICE_GENERATION).complete();
        this.timelinePath = revisedTimelinePath;
        this.plannedOutputDurationSeconds = outputDuration;
        this.voiceOverflowCount = overflowCount;
        stage(ProcessingStageType.TIMELINE_PLANNING).complete();
        this.renderedVideoPath = null;
        this.generatedSubtitlePath = null;
        this.renderedFileSizeBytes = null;
        stage(ProcessingStageType.RENDERING).reset();
        this.status = storyboardReviewEnabled && !storyboardApproved
                ? TaskStatus.WAITING_REVIEW : TaskStatus.READY;
        this.failureReason = null;
    }

    public void deferVoiceGeneration(String reason) {
        this.status = TaskStatus.PROCESSING;
        this.failureReason = null;
        stage(ProcessingStageType.VOICE_GENERATION).defer(reason);
    }

    public void failVoiceGeneration(String reason) {
        this.status = TaskStatus.FAILED;
        this.failureReason = safeFailure(reason);
        stage(ProcessingStageType.VOICE_GENERATION).fail(this.failureReason);
    }

    public void startTimelinePlanning() {
        this.status = TaskStatus.PROCESSING;
        this.failureReason = null;
        stage(ProcessingStageType.TIMELINE_PLANNING).start();
    }

    public void completeTimelinePlanning(String path, double outputDuration, int overflowCount) {
        this.timelinePath = path;
        this.plannedOutputDurationSeconds = outputDuration;
        this.voiceOverflowCount = overflowCount;
        stage(ProcessingStageType.TIMELINE_PLANNING).complete();
    }

    public void failTimelinePlanning(String reason) {
        this.status = TaskStatus.FAILED;
        this.failureReason = safeFailure(reason);
        stage(ProcessingStageType.TIMELINE_PLANNING).fail(this.failureReason);
    }

    public void startRendering() {
        this.status = TaskStatus.PROCESSING;
        this.failureReason = null;
        stage(ProcessingStageType.RENDERING).start();
    }

    public void completeRendering(String videoPath, String subtitlePath, long fileSizeBytes) {
        this.renderedVideoPath = videoPath;
        this.generatedSubtitlePath = subtitlePath;
        this.renderedFileSizeBytes = fileSizeBytes;
        this.status = TaskStatus.COMPLETED;
        stage(ProcessingStageType.RENDERING).complete();
    }

    public void failRendering(String reason) {
        this.status = TaskStatus.FAILED;
        this.failureReason = safeFailure(reason);
        stage(ProcessingStageType.RENDERING).fail(this.failureReason);
    }

    public void cancel(String reason) {
        this.status = TaskStatus.CANCELLED;
        this.failureReason = null;
        stages.stream().filter(stage -> stage.getStatus() == StageStatus.RUNNING)
                .forEach(stage -> stage.defer(reason == null ? "用户取消了任务" : reason));
    }

    private void invalidateAfterScript() {
        this.voiceManifestPath = null;
        this.generatedVoiceSegmentCount = null;
        this.timelinePath = null;
        this.plannedOutputDurationSeconds = null;
        this.voiceOverflowCount = null;
        this.renderedVideoPath = null;
        this.generatedSubtitlePath = null;
        this.renderedFileSizeBytes = null;
        stage(ProcessingStageType.VOICE_GENERATION).reset();
        stage(ProcessingStageType.TIMELINE_PLANNING).reset();
        stage(ProcessingStageType.RENDERING).reset();
        this.status = storyboardReviewEnabled && !storyboardApproved
                ? TaskStatus.WAITING_REVIEW : TaskStatus.READY;
        this.failureReason = null;
    }

    private String safeFailure(String reason) {
        if (reason == null || reason.isBlank()) return "处理失败，未返回错误详情";
        String normalized = reason.strip();
        return normalized.length() <= 900 ? normalized : normalized.substring(0, 897) + "...";
    }

    private ProcessingStage stage(ProcessingStageType type) {
        return stages.stream()
                .filter(stage -> stage.getStageType() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("任务缺少处理阶段：" + type));
    }
}
