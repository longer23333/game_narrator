package cn.longer233.gamenarrator.vision;

import cn.longer233.gamenarrator.media.SceneFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Comparator;

/** Selects scene frames across the complete video with a duration-aware density. */
final class AdaptiveFrameSampler {
    private static final int ABSOLUTE_MAX_FRAMES = 720;

    private AdaptiveFrameSampler() { }

    static List<SceneFrame> sample(List<SceneFrame> frames, int configuredMaximum) {
        if (frames.isEmpty()) return List.of();
        List<SceneFrame> coverageFrames = new ArrayList<>(frames.size());
        List<SceneFrame> eventCenters = new ArrayList<>();
        List<SceneFrame> ordinary = new ArrayList<>();
        double durationSeconds = 0;
        for (SceneFrame frame : frames) {
            durationSeconds = Math.max(durationSeconds, frame.timestampSeconds());
            if (isEventWindow(frame) && !isCenter(frame)) continue;
            coverageFrames.add(frame);
            if (isCenter(frame)) eventCenters.add(frame);
            else ordinary.add(frame);
        }
        int desired = desiredFrameCount(durationSeconds);
        int limit = configuredMaximum > 0 ? Math.min(desired, configuredMaximum) : desired;
        limit = Math.min(coverageFrames.size(), Math.max(1, limit));
        LinkedHashSet<SceneFrame> selected = new LinkedHashSet<>();
        int eventBudget = Math.min(eventCenters.size(), Math.max(1, limit / 3));
        selected.addAll(evenly(eventCenters, eventBudget));
        selected.addAll(evenly(ordinary, limit - selected.size()));
        if (selected.size() < limit) selected.addAll(evenly(coverageFrames, limit));
        return selected.stream().sorted(Comparator.comparingDouble(SceneFrame::timestampSeconds)).limit(limit).toList();
    }

    private static List<SceneFrame> evenly(List<SceneFrame> frames, int limit) {
        if (frames.isEmpty() || limit <= 0) return List.of();
        if (frames.size() <= limit) return frames;
        if (limit == 1) return List.of(frames.getFirst());
        List<SceneFrame> selected = new ArrayList<>(limit);
        java.util.HashSet<SceneFrame> seen = new java.util.HashSet<>(limit * 2);
        for (int index = 0; index < limit; index++) {
            int sourceIndex = (int) Math.round(index * (frames.size() - 1.0) / (limit - 1.0));
            SceneFrame frame = frames.get(sourceIndex);
            if (seen.add(frame)) selected.add(frame);
        }
        return List.copyOf(selected);
    }

    static boolean isEventWindow(SceneFrame frame) {
        return frame.samplingReason() != null && frame.samplingReason().endsWith("_WINDOW");
    }

    static boolean isCenter(SceneFrame frame) {
        return isEventWindow(frame) && frame.eventAnchorSeconds() != null
                && Math.abs(frame.timestampSeconds() - frame.eventAnchorSeconds()) < .08;
    }

    static int desiredFrameCount(double durationSeconds) {
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0) return 12;
        double interval = durationSeconds <= 10 * 60 ? 5
                : durationSeconds <= 60 * 60 ? 10 : 15;
        return Math.min(ABSOLUTE_MAX_FRAMES, Math.max(12, (int) Math.ceil(durationSeconds / interval) + 1));
    }
}
