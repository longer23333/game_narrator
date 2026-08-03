package cn.longer233.gamenarrator.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

@Component
public class PexelsAssetClient {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final String apiKey;

    public PexelsAssetClient(ObjectMapper mapper,
                             @Value("${game-narrator.asset-library.pexels.base-url:https://api.pexels.com}") String baseUrl,
                             @Value("${game-narrator.asset-library.pexels.api-key:}") String apiKey,
                             @Value("${game-narrator.asset-library.pexels.enabled:true}") boolean enabled,
                             @Value("${game-narrator.asset-library.request-timeout-seconds:6}") int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("User-Agent", "GameNarrator/0.1")
                .build();
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.enabled = enabled;
    }

    public boolean configured() { return enabled && !apiKey.isBlank(); }

    public boolean supports(String assetType) {
        return configured() && ("VIDEO".equalsIgnoreCase(assetType) || "MEME".equalsIgnoreCase(assetType));
    }

    public JsonNode search(AssetSearchRequest request) {
        boolean video = "VIDEO".equalsIgnoreCase(request.assetType());
        String uri = UriComponentsBuilder.fromPath(video ? "/videos/search" : "/v1/search")
                .queryParam("query", request.query())
                .queryParam("page", request.page() == null ? 1 : request.page())
                .queryParam("per_page", request.pageSize() == null ? 20 : request.pageSize())
                .build().encode().toUriString();
        JsonNode response = client.get().uri(uri).header("Authorization", apiKey)
                .retrieve().body(JsonNode.class);
        ObjectNode normalized = mapper.createObjectNode();
        ArrayNode results = normalized.putArray("results");
        JsonNode entries = response == null ? mapper.createArrayNode() : response.path(video ? "videos" : "photos");
        for (JsonNode item : entries) results.add(video ? normalizeVideo(item) : normalizePhoto(item));
        return normalized;
    }

    private ObjectNode normalizePhoto(JsonNode item) {
        ObjectNode out = mapper.createObjectNode();
        String creator = item.path("photographer").asText("Pexels contributor");
        out.put("id", item.path("id").asText());
        out.put("title", item.path("alt").asText("Pexels photo " + item.path("id").asText()));
        out.put("creator", creator);
        out.put("foreign_landing_url", item.path("url").asText());
        out.put("thumbnail", item.path("src").path("medium").asText());
        out.put("url", item.path("src").path("original").asText());
        rights(out, "Photo by " + creator + " on Pexels");
        out.putArray("tags");
        return out;
    }

    private ObjectNode normalizeVideo(JsonNode item) {
        ObjectNode out = mapper.createObjectNode();
        String creator = item.path("user").path("name").asText("Pexels contributor");
        out.put("id", item.path("id").asText());
        out.put("title", "Pexels video " + item.path("id").asText());
        out.put("creator", creator);
        out.put("foreign_landing_url", item.path("url").asText());
        out.put("thumbnail", item.path("image").asText());
        JsonNode best = null;
        for (JsonNode file : item.path("video_files")) {
            int width = file.path("width").asInt();
            if (best == null || (width <= 1920 && width > best.path("width").asInt())) best = file;
        }
        if (best != null) out.put("url", best.path("link").asText());
        out.put("duration", item.path("duration").asLong() * 1000L);
        rights(out, "Video by " + creator + " on Pexels");
        out.putArray("tags");
        return out;
    }

    private void rights(ObjectNode out, String attribution) {
        out.put("license", "PEXELS_LICENSE");
        out.put("license_url", "https://www.pexels.com/license/");
        out.put("attribution", attribution);
    }
}
