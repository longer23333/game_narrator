package cn.longer233.gamenarrator.event;

public record GameEventEvidence(
        String sourceType,
        String content,
        double timestampSeconds,
        Integer frameIndex
) {
}
