package cn.longer233.gamenarrator.task.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "processing_stages",
       uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "stageType"}))
public class ProcessingStage {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private VideoTask task;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_type", nullable = false, length = 40)
    private ProcessingStageType stageType;

    @Column(nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StageStatus status;

    @Column(nullable = false)
    private int progress;

    @Column(length = 1000)
    private String errorMessage;

    protected ProcessingStage() {
    }

    ProcessingStage(VideoTask task, ProcessingStageType stageType, int sequenceNumber) {
        this.id = UUID.randomUUID();
        this.task = task;
        this.stageType = stageType;
        this.sequenceNumber = sequenceNumber;
        this.status = StageStatus.PENDING;
        this.progress = 0;
    }

    public UUID getId() { return id; }
    public ProcessingStageType getStageType() { return stageType; }
    public int getSequenceNumber() { return sequenceNumber; }
    public StageStatus getStatus() { return status; }
    public int getProgress() { return progress; }
    public String getErrorMessage() { return errorMessage; }

    public void start() {
        this.status = StageStatus.RUNNING;
        this.progress = 10;
        this.errorMessage = null;
    }

    public void complete() {
        this.status = StageStatus.COMPLETED;
        this.progress = 100;
        this.errorMessage = null;
    }

    public void updateProgress(int progress) {
        if (this.status != StageStatus.RUNNING) return;
        this.progress = Math.max(this.progress, Math.min(99, Math.max(10, progress)));
    }

    public void fail(String message) {
        this.status = StageStatus.FAILED;
        this.progress = 0;
        this.errorMessage = message == null || message.length() <= 900 ? message : message.substring(0, 897) + "...";
    }

    public void defer(String message) {
        this.status = StageStatus.PENDING;
        this.progress = 0;
        this.errorMessage = message == null || message.length() <= 900 ? message : message.substring(0, 897) + "...";
    }

    public void prepareRetry() {
        if (this.status != StageStatus.FAILED) {
            throw new IllegalStateException("Only a failed stage can be retried");
        }
        this.status = StageStatus.PENDING;
        this.progress = 0;
        this.errorMessage = null;
    }

    public void reset() {
        this.status = StageStatus.PENDING;
        this.progress = 0;
        this.errorMessage = null;
    }
}
