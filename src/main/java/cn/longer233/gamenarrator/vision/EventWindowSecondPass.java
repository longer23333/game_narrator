package cn.longer233.gamenarrator.vision;

import cn.longer233.gamenarrator.media.SceneFrame;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.ArrayList;
import java.util.Locale;

/** Chooses pre/post event frames only after the center-frame first pass indicates a real event. */
final class EventWindowSecondPass {
    private EventWindowSecondPass() { }

    static List<SceneFrame> select(List<SceneFrame> allFrames, List<SceneFrame> firstPass,
                                   List<FrameUnderstanding> analyses, int maximum) {
        if (maximum <= 0 || analyses.isEmpty()) return List.of();
        Set<Integer> analyzed = new HashSet<>();
        firstPass.forEach(frame -> analyzed.add(frame.index()));
        Map<Double, EventBudget> anchors = new java.util.LinkedHashMap<>();
        for (FrameUnderstanding analysis : analyses) {
            SceneFrame source = firstPass.stream().filter(frame -> frame.index() == analysis.index()).findFirst().orElse(null);
            if (source == null || !AdaptiveFrameSampler.isCenter(source)) continue;
            boolean explicitEvent = analysis.eventType() != null
                    && !Set.of("其他", "其它", "UNKNOWN", "OTHER").contains(analysis.eventType().toUpperCase());
            if (analysis.excitementScore() >= 60 || explicitEvent || !analysis.ocrText().isBlank()) {
                anchors.put(source.eventAnchorSeconds(), EventBudget.forAnalysis(analysis));
            }
        }
        List<SceneFrame> selected = new ArrayList<>();
        anchors.entrySet().stream()
                .sorted(Map.Entry.<Double, EventBudget>comparingByValue(
                        Comparator.comparingInt(EventBudget::priority).reversed()))
                .forEach(entry -> allFrames.stream()
                        .filter(AdaptiveFrameSampler::isEventWindow)
                        .filter(frame -> !analyzed.contains(frame.index()))
                        .filter(frame -> frame.eventAnchorSeconds() != null
                                && Math.abs(frame.eventAnchorSeconds() - entry.getKey()) < .001)
                        .sorted(Comparator.comparingDouble(frame ->
                                entry.getValue().distancePreference(frame.timestampSeconds(), entry.getKey())))
                        .limit(entry.getValue().frames())
                        .forEach(selected::add));
        return selected.stream().distinct().limit(maximum)
                .sorted(Comparator.comparingDouble(SceneFrame::timestampSeconds)).toList();
    }

    private record EventBudget(int frames, int priority, boolean preferWide) {
        static EventBudget forAnalysis(FrameUnderstanding analysis) {
            String evidence = (analysis.eventType() + " " + analysis.description() + " " + analysis.ocrText())
                    .toLowerCase(Locale.ROOT);
            if (containsAny(evidence, "kill", "击杀", "parry", "弹反", "处决")) return new EventBudget(6, 100, false);
            if (containsAny(evidence, "boss", "phase", "阶段", "转阶段", "形态")) return new EventBudget(6, 90, true);
            if (analysis.excitementScore() >= 80) return new EventBudget(4, 70, false);
            return new EventBudget(2, 40, false);
        }

        double distancePreference(double timestamp, double anchor) {
            double distance = Math.abs(timestamp - anchor);
            return preferWide ? -distance : distance;
        }

        private static boolean containsAny(String value, String... needles) {
            for (String needle : needles) if (value.contains(needle)) return true;
            return false;
        }
    }
}
