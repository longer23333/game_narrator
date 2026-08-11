package cn.longer233.gamenarrator.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class AiResponseJson {
    private AiResponseJson() { }
    static JsonNode parse(ObjectMapper mapper, String content) throws Exception {
        String cleaned = content.trim().replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "");
        return mapper.readTree(cleaned);
    }
}
