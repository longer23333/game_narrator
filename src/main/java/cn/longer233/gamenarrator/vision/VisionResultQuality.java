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
        // 模型没有给置信度时，只能得到一个推断值；它只用于排序和展示，不能拿去当门禁依据，
        // 否则等于用我们自己编的数字决定要不要重跑。
        boolean confidenceKnown = node.has("confidenceScore") && node.path("confidenceScore").isNumber();
        double confidence = confidenceKnown
                ? normalizedConfidence(node.path("confidenceScore").asDouble())
                : inferredConfidence(node, description, score);
        if (confidenceKnown && confidence < minimumConfidence) issues.add("LOW_CONFIDENCE");
        return new Assessment(issues.isEmpty(), confidence, confidenceKnown, List.copyOf(issues));
    }

    static boolean allZero(List<FrameUnderstanding> frames) {
        return !frames.isEmpty() && frames.stream().allMatch(frame -> frame.excitementScore() == 0);
    }

    /**
     * 只在模型没有给出 confidenceScore 时使用。这个值是根据描述长度、事件类型、OCR、评分拼出来的，
     * 仅用于排序和展示，绝不参与是否接受结果的判断。
     */
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

    /** @param confidenceKnown 模型是否真的给了置信度；false 时 confidence 只是推断值，不能当门禁依据 */
    record Assessment(boolean acceptable, double confidence, boolean confidenceKnown, List<String> issues) { }
}
