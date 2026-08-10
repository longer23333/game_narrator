package cn.longer233.gamenarrator.mobile;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure timeline projection used by export composition: maps each clip onto
 * the global output timeline so multi-clip exports keep stable offsets.
 */
public final class ExportTimelinePlan {
    private ExportTimelinePlan() { }

    public static List<Long> mainVideoStartMs(List<TimelineClip> clips) {
        List<Long> starts = new ArrayList<>();
        if (clips == null) return starts;
        long timelineMs = 0;
        for (TimelineClip clip : clips) {
            if (clip == null) continue;
            if ("V1".equals(clip.track())) starts.add(timelineMs);
            timelineMs += clip.durationMs();
        }
        return starts;
    }

    public static long totalDurationMs(List<TimelineClip> clips) {
        long total = 0;
        if (clips == null) return total;
        for (TimelineClip clip : clips) {
            if (clip != null) total += clip.durationMs();
        }
        return total;
    }
}
