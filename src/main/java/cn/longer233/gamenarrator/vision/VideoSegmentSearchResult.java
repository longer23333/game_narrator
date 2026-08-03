package cn.longer233.gamenarrator.vision;

import java.util.UUID;

public record VideoSegmentSearchResult(
        UUID taskId, String taskName, int frameIndex, double timestampSeconds,
        String eventType, String description, double similarity
) {
}
