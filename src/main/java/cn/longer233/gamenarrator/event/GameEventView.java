package cn.longer233.gamenarrator.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record GameEventView(
        UUID id,
        UUID taskId,
        double startSeconds,
        double endSeconds,
        double anchorSeconds,
        String eventType,
        double confidence,
        int importance,
        String description,
        List<GameEventEvidence> evidence,
        String confirmationStatus,
        boolean manuallyEdited,
        String knowledgePackCode,
        OffsetDateTime updatedAt
) {
}
