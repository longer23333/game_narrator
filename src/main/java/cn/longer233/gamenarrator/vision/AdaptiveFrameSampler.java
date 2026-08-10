package cn.longer233.gamenarrator.vision;

import cn.longer233.gamenarrator.media.SceneFrame;

import java.util.ArrayList;
import java.util.List;

/** Selects scene frames across the complete video with a duration-aware density. */
final class AdaptiveFrameSampler {
    private static final int ABSOLUTE_MAX_FRAMES = 720;

    private AdaptiveFrameSampler() { }

    static List<SceneFrame> sample(List<SceneFrame> frames, int configuredMaximum) {
        if (frames.isEmpty()) return List.of();
        double durationSeconds = frames.stream().mapToDouble(SceneFrame::timestampSeconds).max().orElse(0);
        int desired = desiredFrameCount(durationSeconds);
        int limit = configuredMaximum > 0 ? Math.min(desired, configuredMaximum) : desired;
        limit = Math.min(frames.size(), Math.max(1, limit));
        if (frames.size() <= limit) return frames;
        if (limit == 1) return List.of(frames.getFirst());
        List<SceneFrame> selected = new ArrayList<>(limit);
        for (int index = 0; index < limit; index++) {
            int sourceIndex = (int) Math.round(index * (frames.size() - 1.0) / (limit - 1.0));
            SceneFrame frame = frames.get(sourceIndex);
            if (!selected.contains(frame)) selected.add(frame);
        }
        return List.copyOf(selected);
    }

    static int desiredFrameCount(double durationSeconds) {
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0) return 12;
        double interval = durationSeconds <= 10 * 60 ? 5
                : durationSeconds <= 60 * 60 ? 10 : 15;
        return Math.min(ABSOLUTE_MAX_FRAMES, Math.max(12, (int) Math.ceil(durationSeconds / interval) + 1));
    }
}
