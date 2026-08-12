package cn.longer233.gamenarrator.vision;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class VisionQualityRecovery {
    private VisionQualityRecovery() { }

    static Result recover(String primaryModel, List<String> fallbackModels, int sameModelAttempts,
                          double minimumConfidence, Attempt attempt) throws Exception {
        return recover(primaryModel, fallbackModels, sameModelAttempts, minimumConfidence, false, attempt);
    }

    static Result recover(String primaryModel, List<String> fallbackModels, int sameModelAttempts,
                          double minimumConfidence, boolean requireNonZeroScore, Attempt attempt) throws Exception {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        models.add(primaryModel);
        for (String value : fallbackModels) if (value != null && !value.isBlank()) models.add(value.strip());
        List<AttemptRecord> records = new ArrayList<>();
        for (String model : models) {
            int limit = model.equals(primaryModel) ? Math.max(1, sameModelAttempts) : 1;
            for (int number = 1; number <= limit; number++) {
                try {
                    JsonNode node = attempt.call(model);
                    var quality = VisionResultQuality.assess(node, minimumConfidence, requireNonZeroScore);
                    records.add(new AttemptRecord(model, number, quality.acceptable(), quality.issues(), null));
                    if (quality.acceptable()) return new Result(node, model, quality.confidence(), records, false);
                } catch (Exception exception) {
                    records.add(new AttemptRecord(model, number, false, List.of("REQUEST_FAILED"),
                            limited(exception.getMessage())));
                }
            }
        }
        return new Result(null, null, 0, records, true);
    }

    private static String limited(String value) {
        if (value == null) return null;
        return value.length() <= 240 ? value : value.substring(0, 240);
    }

    @FunctionalInterface interface Attempt { JsonNode call(String model) throws Exception; }
    record AttemptRecord(String model, int attempt, boolean accepted, List<String> issues, String error) { }
    record Result(JsonNode value, String model, double confidence, List<AttemptRecord> attempts, boolean degraded) { }
}
