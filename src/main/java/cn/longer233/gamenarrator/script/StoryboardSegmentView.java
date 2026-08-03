package cn.longer233.gamenarrator.script;

public record StoryboardSegmentView(
        int clipIndex, double startSeconds, double endSeconds,
        String narration, String subtitle, String effectCue,
        String eventType, String description, int finalScore
) {
}
