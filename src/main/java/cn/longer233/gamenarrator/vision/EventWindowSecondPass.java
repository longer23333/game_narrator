package cn.longer233.gamenarrator.vision;

import cn.longer233.gamenarrator.media.SceneFrame;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Chooses pre/post event frames only after the center-frame first pass indicates a real event. */
final class EventWindowSecondPass {
    private EventWindowSecondPass() { }

    static List<SceneFrame> select(List<SceneFrame> allFrames, List<SceneFrame> firstPass,
                                   List<FrameUnderstanding> analyses, int maximum) {
        if (maximum <= 0 || analyses.isEmpty()) return List.of();
        Set<Integer> analyzed = new HashSet<>();
        firstPass.forEach(frame -> analyzed.add(frame.index()));
        Set<Double> anchors = new HashSet<>();
        for (FrameUnderstanding analysis : analyses) {
            SceneFrame source = firstPass.stream().filter(frame -> frame.index() == analysis.index()).findFirst().orElse(null);
            if (source == null || !AdaptiveFrameSampler.isCenter(source)) continue;
            boolean explicitEvent = analysis.eventType() != null
                    && !Set.of("其他", "其它", "UNKNOWN", "OTHER").contains(analysis.eventType().toUpperCase());
            if (analysis.excitementScore() >= 60 || explicitEvent || !analysis.ocrText().isBlank()) {
                anchors.add(source.eventAnchorSeconds());
            }
        }
        return allFrames.stream()
                .filter(AdaptiveFrameSampler::isEventWindow)
                .filter(frame -> !analyzed.contains(frame.index()))
                .filter(frame -> frame.eventAnchorSeconds() != null && anchors.contains(frame.eventAnchorSeconds()))
                .sorted(Comparator.comparingDouble(SceneFrame::timestampSeconds))
                .limit(maximum)
                .toList();
    }
}
