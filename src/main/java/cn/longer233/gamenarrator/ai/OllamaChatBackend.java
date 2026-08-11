package cn.longer233.gamenarrator.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OllamaChatBackend implements AiChatBackend {
    private final ObjectMapper mapper;
    private final AiUsageService usage;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final URI baseUri;

    public OllamaChatBackend(ObjectMapper mapper, AiUsageService usage,
                             @Value("${game-narrator.ollama.base-url:http://localhost:11434}") String baseUrl) {
        this.mapper = mapper;
        this.usage = usage;
        this.baseUri = URI.create(baseUrl);
    }

    @Override public String engineId() { return "ollama"; }

    @Override
    public boolean available() {
        try {
            var request = HttpRequest.newBuilder(baseUri.resolve("/api/tags"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception ignored) { return false; }
    }

    public boolean modelAvailable(String model) {
        try {
            byte[] body = mapper.writeValueAsBytes(Map.of("model", model));
            var request = HttpRequest.newBuilder(baseUri.resolve("/api/show")).timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception ignored) { return false; }
    }

    @Override
    public JsonNode chatJson(String prompt, List<String> images, String model,
                             AiSettingsService.Settings settings, Duration timeout) throws Exception {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        if (!images.isEmpty()) message.put("images", images);
        byte[] body = mapper.writeValueAsBytes(Map.of("model", model, "stream", false, "format", "json",
                "messages", List.of(message), "options", Map.of("temperature", 0.2)));
        var request = HttpRequest.newBuilder(baseUri.resolve("/api/chat")).timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) throw new IllegalStateException("本地模型 HTTP " + response.statusCode());
        JsonNode root = mapper.readTree(response.body());
        usage.record("LOCAL", model, root.path("prompt_eval_count").asLong(), root.path("eval_count").asLong(), 0, 0, 0, 0);
        return AiResponseJson.parse(mapper, root.path("message").path("content").asText());
    }
}
