package cn.longer233.gamenarrator.event;

public record GameEventFact(
        String eventType,
        String description,
        double startSeconds,
        double endSeconds,
        int importance
) {
}
