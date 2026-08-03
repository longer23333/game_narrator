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
        boolean voiceOverflow
) {
}
