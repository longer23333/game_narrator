package cn.longer233.gamenarrator.timeline;

public record TimelineSegment(
        int sequence,
        double outputStartSeconds,
        double outputEndSeconds,
        double sourceStartSeconds,
        double sourceEndSeconds,
        String narration,
        String subtitle,
        String effectCue,
        String voicePath,
        double voiceDurationSeconds,
        boolean voiceOverflow,
        String transitionType,
        double transitionDurationSeconds,
        String transitionDirection,
        String transitionCurve
) {
    public TimelineSegment {
        transitionType = normalize(transitionType, "HARD_CUT");
        transitionDurationSeconds = Double.isFinite(transitionDurationSeconds)
                ? Math.max(0, transitionDurationSeconds)
                : 0;
        transitionDirection = normalize(transitionDirection, "LEFT");
        transitionCurve = normalize(transitionCurve, "TRI");
    }

    public TimelineSegment(int sequence, double outputStartSeconds, double outputEndSeconds,
            double sourceStartSeconds, double sourceEndSeconds, String narration, String subtitle,
            String effectCue, String voicePath, double voiceDurationSeconds, boolean voiceOverflow) {
        this(sequence, outputStartSeconds, outputEndSeconds, sourceStartSeconds, sourceEndSeconds,
                narration, subtitle, effectCue, voicePath, voiceDurationSeconds, voiceOverflow,
                "HARD_CUT", 0, "LEFT", "TRI");
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
