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
public class PixabayAssetClient {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final String apiKey;

    public PixabayAssetClient(ObjectMapper mapper,
                              @Value("${game-narrator.asset-library.pixabay.base-url:https://pixabay.com}") String baseUrl,
                              @Value("${game-narrator.asset-library.pixabay.api-key:}") String apiKey,
                              @Value("${game-narrator.asset-library.pixabay.enabled:true}") boolean enabled,
                              @Value("${game-narrator.asset-library.request-timeout-seconds:6}") int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("User-Agent", "GameNarrator/0.1").build();
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.enabled = enabled;
    }

    public boolean configured() { return enabled && !apiKey.isBlank(); }

    public boolean supports(String assetType) {
        return configured() && ("VIDEO".equalsIgnoreCase(assetType) || "IMAGE".equalsIgnoreCase(assetType));
    }

    public JsonNode search(AssetSearchRequest request) {
        boolean video = "VIDEO".equalsIgnoreCase(request.assetType());
        String uri = UriComponentsBuilder.fromPath(video ? "/api/videos/" : "/api/")
                .queryParam("key", apiKey).queryParam("q", request.query())
                .queryParam("page", request.page() == null ? 1 : request.page())
                .queryParam("per_page", request.pageSize() == null ? 20 : request.pageSize())
                .queryParam("safesearch", true).build().encode().toUriString();
        JsonNode response = client.get().uri(uri).retrieve().body(JsonNode.class);
        ObjectNode normalized = mapper.createObjectNode();
        ArrayNode results = normalized.putArray("results");
        if (response != null) for (JsonNode item : response.path("hits")) results.add(normalize(item, video));
        return normalized;
    }

    private ObjectNode normalize(JsonNode item, boolean video) {
        ObjectNode out = mapper.createObjectNode();
        String creator = item.path("user").asText("Pixabay contributor");
        String tags = item.path("tags").asText();
        out.put("id", item.path("id").asText());
        out.put("title", tags.isBlank() ? "Pixabay " + (video ? "video " : "image ") + item.path("id").asText() : tags);
        out.put("creator", creator);
        out.put("foreign_landing_url", item.path("pageURL").asText());
        out.put("thumbnail", video ? item.path("picture").asText() : item.path("previewURL").asText());
        if (video) {
            JsonNode files = item.path("videos");
            String url = files.path("medium").path("url").asText();
            if (url.isBlank()) url = files.path("small").path("url").asText();
            if (url.isBlank()) url = files.path("large").path("url").asText();
            out.put("url", url);
            out.put("duration", item.path("duration").asLong() * 1000L);
        } else {
            String url = item.path("largeImageURL").asText();
            out.put("url", url.isBlank() ? item.path("webformatURL").asText() : url);
        }
        out.put("license", "PIXABAY_CONTENT_LICENSE");
        out.put("license_url", "https://pixabay.com/service/license-summary/");
        out.put("attribution", (video ? "Video" : "Image") + " by " + creator + " on Pixabay");
        ArrayNode tagList = out.putArray("tags");
        for (String tag : tags.split(",")) if (!tag.isBlank()) tagList.add(tag.trim());
        return out;
    }
}
