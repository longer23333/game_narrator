package cn.longer233.gamenarrator.highlight;

public record HighlightClip(int sourceFrameIndex, double startSeconds, double endSeconds,
        double anchorSeconds, String eventType, String description, int sourceScore, int finalScore,
        boolean locked, boolean excluded) {
    public HighlightClip(int sourceFrameIndex, double startSeconds, double endSeconds,
                         double anchorSeconds, String eventType, String description, int sourceScore, int finalScore) {
        this(sourceFrameIndex, startSeconds, endSeconds, anchorSeconds, eventType, description,
                sourceScore, finalScore, false, false);
    }
    public double durationSeconds() { return endSeconds - startSeconds; }
}
