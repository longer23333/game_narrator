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
        Set<Integer> analyzed = new HashSet<>(Math.max(16, firstPass.size() * 2));
        Map<Integer, SceneFrame> firstPassByIndex = new java.util.HashMap<>(Math.max(16, firstPass.size() * 2));
        for (SceneFrame frame : firstPass) {
            analyzed.add(frame.index());
            firstPassByIndex.putIfAbsent(frame.index(), frame);
        }
        Map<Double, List<SceneFrame>> eventFramesByAnchor = new java.util.HashMap<>();
        for (SceneFrame frame : allFrames) {
            if (AdaptiveFrameSampler.isEventWindow(frame) && !analyzed.contains(frame.index())
                    && frame.eventAnchorSeconds() != null) {
                eventFramesByAnchor.computeIfAbsent(frame.eventAnchorSeconds(), ignored -> new ArrayList<>()).add(frame);
            }
        }
        Map<Double, EventBudget> anchors = new java.util.LinkedHashMap<>();
        for (FrameUnderstanding analysis : analyses) {
            SceneFrame source = firstPassByIndex.get(analysis.index());
            if (source == null || !AdaptiveFrameSampler.isCenter(source)) continue;
            boolean explicitEvent = analysis.eventType() != null
                    && !Set.of("其他", "其它", "UNKNOWN", "OTHER").contains(analysis.eventType().toUpperCase(Locale.ROOT));
            if (analysis.excitementScore() >= 60 || explicitEvent || !analysis.ocrText().isBlank()) {
                anchors.put(source.eventAnchorSeconds(), EventBudget.forAnalysis(analysis));
            }
        }
        List<SceneFrame> selected = new ArrayList<>();
        anchors.entrySet().stream()
                .sorted(Map.Entry.<Double, EventBudget>comparingByValue(
                        Comparator.comparingInt(EventBudget::priority).reversed()))
                .forEach(entry -> eventFramesByAnchor.entrySet().stream()
                        .filter(candidate -> Math.abs(candidate.getKey() - entry.getKey()) < .001)
                        .flatMap(candidate -> candidate.getValue().stream())
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
