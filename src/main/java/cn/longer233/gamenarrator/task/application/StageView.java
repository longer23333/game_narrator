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
        String errorMessage,
        String subprogressUnit,
        Integer subprogressCurrent,
        Integer subprogressTotal,
        String subprogressDetail
) {
    static StageView from(ProcessingStage stage) {
        return new StageView(
                stage.getId(),
                stage.getStageType(),
                stage.getSequenceNumber(),
                stage.getStatus(),
                stage.getProgress(),
                stage.getErrorMessage(),
                stage.getSubprogressUnit(),
                stage.getSubprogressCurrent(),
                stage.getSubprogressTotal(),
                stage.getSubprogressDetail()
        );
    }
}
