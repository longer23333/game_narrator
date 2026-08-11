package cn.longer233.gamenarrator.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;

/** Protocol adapter used by the chat router. */
public interface AiChatBackend extends ModelAdapter {
    JsonNode chatJson(String prompt, List<String> images, String model,
                      AiSettingsService.Settings settings, Duration timeout) throws Exception;
}
