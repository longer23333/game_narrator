package cn.longer233.gamenarrator.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class CloudAiChatBackend implements AiChatBackend {
    private final ObjectMapper mapper;
    private final AiUsageService usage;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final int maxAttempts;
    private final long initialBackoffMillis;

    public CloudAiChatBackend(ObjectMapper mapper, AiUsageService usage,
            @Value("${game-narrator.ai.retry.max-attempts:3}") int maxAttempts,
            @Value("${game-narrator.ai.retry.initial-backoff-ms:250}") long initialBackoffMillis) {
        this.mapper = mapper;
        this.usage = usage;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoffMillis = Math.max(0, initialBackoffMillis);
    }

    @Override public String engineId() { return "cloud"; }
    @Override public boolean available() { return true; }

    @Override
    public JsonNode chatJson(String prompt, List<String> images, String model,
                             AiSettingsService.Settings value, Duration timeout) throws Exception {
        if (value.apiKey().isBlank()) throw new IllegalStateException("请先在 AI 模型设置中填写云端 API Key");
        return switch (value.provider()) {
            case "ANTHROPIC" -> anthropic(prompt, images, model, value, timeout);
            case "GEMINI" -> gemini(prompt, images, model, value, timeout);
            default -> openAi(prompt, images, model, value, timeout);
        };
    }

    private JsonNode openAi(String prompt, List<String> images, String model,
                            AiSettingsService.Settings value, Duration timeout) throws Exception {
        Object content = prompt;
        if (!images.isEmpty()) {
            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(Map.of("type", "text", "text", prompt));
            images.forEach(image -> parts.add(Map.of("type", "image_url", "image_url",
                    Map.of("url", "data:image/jpeg;base64," + image))));
            content = parts;
        }
        byte[] body = mapper.writeValueAsBytes(Map.of("model", model,
                "messages", List.of(Map.of("role", "user", "content", content)),
                "temperature", 0.2, "response_format", Map.of("type", "json_object")));
        var request = HttpRequest.newBuilder(URI.create(openAiEndpoint(value.baseUrl()))).timeout(timeout)
                .header("Authorization", "Bearer " + value.apiKey()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var response = send(request);
        JsonNode root = mapper.readTree(response.body());
        if (response.statusCode() / 100 != 2) {
            if (AdaptiveAiChatClient.isContentRejected(response.body()))
                throw new AiContentRejectedException("云端模型拒绝了该条内容，已改用本地规则继续处理");
            throw providerError(value.provider(), response.statusCode(), root);
        }
        JsonNode tokens = root.path("usage");
        record(value, model, tokens.path("prompt_tokens").asLong(), tokens.path("completion_tokens").asLong(),
                tokens.path("prompt_tokens_details").path("cached_tokens").asLong());
        return AiResponseJson.parse(mapper, root.path("choices").path(0).path("message").path("content").asText());
    }

    private JsonNode anthropic(String prompt, List<String> images, String model,
                               AiSettingsService.Settings value, Duration timeout) throws Exception {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt));
        images.forEach(image -> content.add(Map.of("type", "image", "source",
                Map.of("type", "base64", "media_type", "image/jpeg", "data", image))));
        byte[] body = mapper.writeValueAsBytes(Map.of("model", model, "max_tokens", 4096,
                "messages", List.of(Map.of("role", "user", "content", content))));
        String endpoint = value.baseUrl().replaceAll("/+$", "");
        if (!endpoint.endsWith("/messages")) endpoint += "/messages";
        var request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(timeout)
                .header("x-api-key", value.apiKey()).header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var response = send(request);
        JsonNode root = mapper.readTree(response.body());
        if (response.statusCode() / 100 != 2) throw providerError("Anthropic", response.statusCode(), root);
        JsonNode tokens = root.path("usage");
        record(value, model, tokens.path("input_tokens").asLong(), tokens.path("output_tokens").asLong(),
                tokens.path("cache_read_input_tokens").asLong());
        return AiResponseJson.parse(mapper, root.path("content").path(0).path("text").asText());
    }

    private JsonNode gemini(String prompt, List<String> images, String model,
                            AiSettingsService.Settings value, Duration timeout) throws Exception {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt));
        images.forEach(image -> parts.add(Map.of("inlineData", Map.of("mimeType", "image/jpeg", "data", image))));
        byte[] body = mapper.writeValueAsBytes(Map.of("contents", List.of(Map.of("role", "user", "parts", parts)),
                "generationConfig", Map.of("responseMimeType", "application/json")));
        String endpoint = value.baseUrl().replaceAll("/+$", "") + "/models/"
                + URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent";
        var request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(timeout)
                .header("x-goog-api-key", value.apiKey()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var response = send(request);
        JsonNode root = mapper.readTree(response.body());
        if (response.statusCode() / 100 != 2) throw providerError("Gemini", response.statusCode(), root);
        JsonNode tokens = root.path("usageMetadata");
        record(value, model, tokens.path("promptTokenCount").asLong(), tokens.path("candidatesTokenCount").asLong(),
                tokens.path("cachedContentTokenCount").asLong());
        return AiResponseJson.parse(mapper, root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        for (int attempt = 1; ; attempt++) {
            try {
                var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (!AdaptiveAiChatClient.retryableStatus(response.statusCode()) || attempt >= maxAttempts) return response;
            } catch (java.io.IOException failure) {
                if (attempt >= maxAttempts) throw failure;
            }
            long delay = Math.min(5_000, initialBackoffMillis << Math.min(20, attempt - 1));
            if (delay > 0) Thread.sleep(delay);
        }
    }

    private String openAiEndpoint(String baseUrl) {
        String value = baseUrl.trim();
        if (value.contains("/chat/completions")) return value;
        int query = value.indexOf('?');
        if (query < 0) return value.replaceAll("/+$", "") + "/chat/completions";
        return value.substring(0, query).replaceAll("/+$", "") + "/chat/completions" + value.substring(query);
    }

    private IllegalStateException providerError(String provider, int status, JsonNode root) {
        String detail = root.path("error").path("message").asText(root.path("message").asText(""));
        if (detail.length() > 240) detail = detail.substring(0, 240);
        return new IllegalStateException(provider + " HTTP " + status + (detail.isBlank() ? "" : "：" + detail));
    }

    private void record(AiSettingsService.Settings value, String model, long input, long output, long cached) {
        usage.record(value.provider(), model, input, output, cached, value.inputPricePerMillion(),
                value.outputPricePerMillion(), value.cachedInputPricePerMillion());
    }
}
