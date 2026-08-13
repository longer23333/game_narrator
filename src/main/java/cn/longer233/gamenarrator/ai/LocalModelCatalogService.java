package cn.longer233.gamenarrator.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class LocalModelCatalogService {
    private static final List<Recommendation> RECOMMENDATIONS = List.of(
            new Recommendation("qwen2.5vl:3b", "视觉", 3_200, 6_000, "轻量视觉理解，适合 8GB 显存设备"),
            new Recommendation("qwen2.5vl:7b", "视觉", 6_000, 10_000, "更强的画面和文字理解"),
            new Recommendation("qwen2.5:3b", "文案", 2_500, 5_000, "轻量中文文案生成"),
            new Recommendation("qwen2.5:7b", "文案", 5_500, 9_000, "质量与资源占用平衡"),
            new Recommendation("deepseek-r1:7b", "文案", 5_500, 9_000, "偏推理与结构化策划"));

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final URI baseUri;

    @Autowired
    public LocalModelCatalogService(ObjectMapper mapper,
            @Value("${game-narrator.ollama.base-url:http://localhost:11434}") String baseUrl) {
        this(mapper, baseUrl, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    LocalModelCatalogService(ObjectMapper mapper, String baseUrl, HttpClient http) {
        this.mapper = mapper;
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        this.http = http;
    }

    public Catalog catalog(String activeVision, String activeText) {
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("api/tags"))
                    .timeout(Duration.ofSeconds(4)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) return unavailable(activeVision, activeText, "Ollama HTTP " + response.statusCode());
            JsonNode root = mapper.readTree(response.body());
            List<Model> models = new ArrayList<>();
            for (JsonNode node : root.path("models")) {
                String name = node.path("name").asText();
                long size = node.path("size").asLong();
                Recommendation known = recommendation(name);
                models.add(new Model(name, size, true, "READY", known == null ? estimateMemory(size) : known.memoryMb(),
                        known == null ? estimateVram(size) : known.vramMb(), known == null ? inferKind(name) : known.kind(),
                        Objects.equals(name, activeVision), Objects.equals(name, activeText)));
            }
            return new Catalog(true, "READY", "Ollama 可用", activeVision, activeText, models, RECOMMENDATIONS);
        } catch (Exception exception) {
            return unavailable(activeVision, activeText, exception.getMessage());
        }
    }

    public DownloadResult download(String requestedName) {
        String name = validateName(requestedName);
        try {
            byte[] body = mapper.writeValueAsBytes(Map.of("name", name, "stream", false));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("api/pull"))
                    .timeout(Duration.ofHours(2)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("Ollama HTTP " + response.statusCode());
            String status = mapper.readTree(response.body()).path("status").asText("success");
            return new DownloadResult(name, true, status);
        } catch (Exception exception) {
            throw new IllegalStateException("模型下载失败：" + exception.getMessage(), exception);
        }
    }

    private Catalog unavailable(String vision, String text, String detail) {
        return new Catalog(false, "OFFLINE", "Ollama 不可用：" + Objects.toString(detail, "连接失败"),
                vision, text, List.of(), RECOMMENDATIONS);
    }

    private Recommendation recommendation(String name) {
        return RECOMMENDATIONS.stream().filter(item -> sameModel(item.name(), name)).findFirst().orElse(null);
    }

    private boolean sameModel(String expected, String actual) {
        return actual.equals(expected) || actual.startsWith(expected + "-") || actual.startsWith(expected + ":");
    }

    static String validateName(String value) {
        String name = Objects.toString(value, "").trim();
        if (name.isBlank() || name.length() > 120 || !name.matches("[A-Za-z0-9._/-]+(?::[A-Za-z0-9._-]+)?"))
            throw new IllegalArgumentException("模型名称格式无效");
        return name;
    }

    private String inferKind(String name) {
        String value = name.toLowerCase(Locale.ROOT);
        return value.contains("vl") || value.contains("vision") || value.contains("llava") ? "视觉" : "文案";
    }
    private long estimateMemory(long bytes) { return Math.max(1024, Math.round(bytes / 1024d / 1024d * 1.35)); }
    private long estimateVram(long bytes) { return Math.max(1024, Math.round(bytes / 1024d / 1024d * 1.15)); }

    public record Model(String name, long sizeBytes, boolean installed, String health, long memoryMb, long vramMb,
                        String kind, boolean activeVision, boolean activeText) { }
    public record Recommendation(String name, String kind, long memoryMb, long vramMb, String description) { }
    public record Catalog(boolean serviceAvailable, String health, String message, String activeVisionModel,
                          String activeTextModel, List<Model> installed, List<Recommendation> recommendations) { }
    public record DownloadResult(String name, boolean installed, String status) { }
}
