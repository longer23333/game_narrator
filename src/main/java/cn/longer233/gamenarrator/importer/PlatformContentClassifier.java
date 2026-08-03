package cn.longer233.gamenarrator.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class PlatformContentClassifier {
    private static final Set<String> CODES = Set.of(
            "CREATOR_UPLOAD", "ORGANIZATION_UPLOAD", "PLATFORM_OFFICIAL", "UNKNOWN");
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String model;

    public PlatformContentClassifier(ObjectMapper objectMapper,
                                     @Value("${game-narrator.ollama.base-url}") String baseUrl,
                                     @Value("${game-narrator.ollama.script-model}") String model) {
        this.objectMapper = objectMapper;
        this.model = model;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public ContentOriginAssessment assess(ResolvedMedia media) {
        ContentOriginAssessment fallback = fallback(media);
        try {
            String prompt = """
                    根据视频平台公开元数据，推测内容账号性质。只返回 JSON：
                    {"code":"CREATOR_UPLOAD|ORGANIZATION_UPLOAD|PLATFORM_OFFICIAL|UNKNOWN","confidence":0到1,"reason":"简短依据"}
                    CREATOR_UPLOAD 表示普通创作者或个人投稿；ORGANIZATION_UPLOAD 表示机构、媒体或品牌账号投稿；
                    PLATFORM_OFFICIAL 仅表示视频平台自身官方账号；无法可靠判断必须返回 UNKNOWN。
                    这不是版权或下载授权判断，不得根据标题内容推断用户拥有权利。
                    平台：%s
                    标题：%s
                    投稿者：%s
                    标签：%s
                    """.formatted(media.platform(), media.title(), media.creator(), media.tags());
            JsonNode response = client.post().uri("/api/generate").body(Map.of(
                    "model", model, "prompt", prompt, "stream", false, "format", "json",
                    "options", Map.of("temperature", 0.0, "num_predict", 100)
            )).retrieve().body(JsonNode.class);
            JsonNode result = objectMapper.readTree(response.path("response").asText("{}"));
            String code = result.path("code").asText("UNKNOWN").toUpperCase(Locale.ROOT);
            if (!CODES.contains(code)) return fallback;
            double confidence = Math.max(0, Math.min(1, result.path("confidence").asDouble(0)));
            if (confidence < 0.55) return fallback;
            String reason = result.path("reason").asText("AI 根据公开元数据推测").trim();
            return new ContentOriginAssessment(code, label(code), confidence,
                    reason.substring(0, Math.min(200, reason.length())));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private ContentOriginAssessment fallback(ResolvedMedia media) {
        String combined = (String.valueOf(media.creator()) + " " + String.valueOf(media.title())).toLowerCase(Locale.ROOT);
        if (combined.contains("官方") || combined.contains("official")) {
            return new ContentOriginAssessment("UNKNOWN", label("UNKNOWN"), 0.35,
                    "仅检测到“官方”字样，无法确认是否为平台自身账号");
        }
        if (media.creator() != null && !media.creator().isBlank()) {
            return new ContentOriginAssessment("CREATOR_UPLOAD", label("CREATOR_UPLOAD"), 0.55,
                    "平台元数据提供了明确投稿者，暂按创作者投稿标注");
        }
        return new ContentOriginAssessment("UNKNOWN", label("UNKNOWN"), 0.0, "公开元数据不足");
    }

    private String label(String code) {
        return switch (code) {
            case "CREATOR_UPLOAD" -> "疑似创作者投稿";
            case "ORGANIZATION_UPLOAD" -> "疑似机构账号投稿";
            case "PLATFORM_OFFICIAL" -> "疑似平台官方内容";
            default -> "来源性质未知";
        };
    }
}
