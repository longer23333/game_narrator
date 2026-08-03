package cn.longer233.gamenarrator.export;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExportJobView(
        UUID id,
        UUID projectId,
        UUID presetId,
        String presetName,
        String exportName,
        String container,
        String status,
        int progress,
        String outputFileName,
        Long outputSizeBytes,
        String errorMessage,
        int downloadCount,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
}
