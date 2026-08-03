package cn.longer233.gamenarrator.highlight;

public record HighlightClip(int sourceFrameIndex, double startSeconds, double endSeconds,
        double anchorSeconds, String eventType, String description, int sourceScore, int finalScore) {
    public double durationSeconds() { return endSeconds - startSeconds; }
}
