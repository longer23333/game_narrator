package cn.longer233.gamenarrator.vision;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class VisionResultQuality {
    private VisionResultQuality() { }

    static Assessment assess(JsonNode node, double minimumConfidence) {
        return assess(node, minimumConfidence, false);
    }

    static Assessment assess(JsonNode node, double minimumConfidence, boolean requireNonZeroScore) {
        List<String> issues = new ArrayList<>();
        String description = node.path("description").asText("").strip();
        String normalized = description.toLowerCase(Locale.ROOT);
        if (description.isBlank() || normalized.contains("未识别") || normalized.contains("unknown")
                || normalized.contains("无法判断")) issues.add("EMPTY_DESCRIPTION");
        int score = Math.max(0, Math.min(100, node.path("excitementScore").asInt(0)));
        if (requireNonZeroScore && score == 0) issues.add("ZERO_SCORE");
        double confidence = node.has("confidenceScore")
                ? normalizedConfidence(node.path("confidenceScore").asDouble())
                : inferredConfidence(node, description, score);
        if (confidence < minimumConfidence) issues.add("LOW_CONFIDENCE");
        return new Assessment(issues.isEmpty(), confidence, List.copyOf(issues));
    }

    static boolean allZero(List<FrameUnderstanding> frames) {
        return !frames.isEmpty() && frames.stream().allMatch(frame -> frame.excitementScore() == 0);
    }

    private static double inferredConfidence(JsonNode node, String description, int score) {
        double value = description.length() >= 8 ? .45 : .15;
        String event = node.path("eventType").asText("");
        if (!event.isBlank() && !event.equals("其他") && !event.equalsIgnoreCase("OTHER")) value += .2;
        if (!node.path("ocrText").asText("").isBlank()) value += .15;
        if (score > 0) value += .2;
        return Math.min(1, value);
    }

    private static double normalizedConfidence(double value) {
        return Math.max(0, Math.min(1, value > 1 ? value / 100.0 : value));
    }

    record Assessment(boolean acceptable, double confidence, List<String> issues) { }
}
