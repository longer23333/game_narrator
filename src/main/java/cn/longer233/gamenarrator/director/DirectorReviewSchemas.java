package cn.longer233.gamenarrator.director;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded review output keeps small local models from exhausting their context. */
final class DirectorReviewSchemas {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private DirectorReviewSchemas() { }

    static JsonNode opinion() {
        return MAPPER.valueToTree(object(Map.of(
                "summary", text(120), "findings", array(object(Map.of("claim", text(100),
                        "evidence", text(120), "risk", text(80), "recommendation", text(100))), 3),
                "eventIds", array(text(36), 5), "clipIndexes", array(Map.of("type", "integer"), 5),
                "knowledgeRefs", array(text(80), 3), "confidence", score())));
    }

    static JsonNode decision() {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("summary", text(160));
        fields.put("consensusScore", score());
        fields.put("agreements", array(text(100), 3));
        fields.put("disagreements", array(text(100), 3));
        fields.put("finalDecisions", array(object(Map.of("type", text(40), "target", text(60),
                "action", text(100), "reason", text(100), "evidence", text(100))), 3));
        fields.put("narrativeBeats", Map.of("type", "array", "minItems", 5, "maxItems", 5,
                "items", object(Map.of("stage", Map.of("type", "string", "enum",
                        List.of("SETUP", "CRISIS", "REVERSAL", "CLIMAX", "RESULT")),
                        "clipIndex", Map.of("type", "integer"), "paceMultiplier",
                        Map.of("type", "number", "minimum", 0.5, "maximum", 1.8),
                        "musicIntensity", score(), "narrationDirective", text(100)))));
        fields.put("eventIds", array(text(36), 5));
        fields.put("clipIndexes", array(Map.of("type", "integer"), 5));
        fields.put("knowledgeRefs", array(text(80), 3));
        fields.put("requiresUserConfirmation", Map.of("type", "boolean", "const", true));
        return MAPPER.valueToTree(object(fields));
    }

    private static Map<String, Object> text(int limit) { return Map.of("type", "string", "maxLength", limit); }
    private static Map<String, Object> score() { return Map.of("type", "number", "minimum", 0, "maximum", 1); }
    private static Map<String, Object> array(Object item, int limit) { return Map.of("type", "array", "items", item, "maxItems", limit); }
    private static Map<String, Object> object(Map<String, Object> fields) {
        return Map.of("type", "object", "properties", fields, "required", List.copyOf(fields.keySet()), "additionalProperties", false);
    }
}
