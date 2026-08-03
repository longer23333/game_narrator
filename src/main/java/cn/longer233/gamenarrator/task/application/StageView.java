package cn.longer233.gamenarrator.task.application;

import cn.longer233.gamenarrator.task.domain.ProcessingStage;
import cn.longer233.gamenarrator.task.domain.ProcessingStageType;
import cn.longer233.gamenarrator.task.domain.StageStatus;
import java.util.UUID;

public record StageView(
        UUID id,
        ProcessingStageType type,
        int sequence,
        StageStatus status,
        int progress,
        String errorMessage
) {
    static StageView from(ProcessingStage stage) {
        return new StageView(
                stage.getId(),
                stage.getStageType(),
                stage.getSequenceNumber(),
                stage.getStatus(),
                stage.getProgress(),
                stage.getErrorMessage()
        );
    }
}
