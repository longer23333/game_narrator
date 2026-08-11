package cn.longer233.gamenarrator.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;
import java.util.Locale;

@Component
public class WikimediaAssetClient {
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final AssetSearchResilience resilience;

    public WikimediaAssetClient(ObjectMapper objectMapper,
                                @Value("${game-narrator.asset-library.wikimedia.base-url:https://commons.wikimedia.org}") String baseUrl,
                                @Value("${game-narrator.asset-library.wikimedia.enabled:true}") boolean enabled,
                                @Value("${game-narrator.asset-library.wikimedia.request-timeout-seconds:${game-narrator.asset-library.request-timeout-seconds:3}}") int timeoutSeconds,
                                AssetSearchResilience resilience) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.resilience = resilience;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(1, Math.min(10, timeoutSeconds)));
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", "GameNarrator/0.1 graduation-project")
                .build();
    }

    public boolean supports(String assetType) {
        return enabled;
    }

    public JsonNode search(AssetSearchRequest request) {
        int pageSize = request.pageSize() == null ? 20 : request.pageSize();
        int requestedPage = request.page() == null ? 1 : request.page();
        String target = UriComponentsBuilder.fromPath("/w/api.php")
                .queryParam("action", "query")
                .queryParam("generator", "search")
                .queryParam("gsrsearch", request.query())
                .queryParam("gsrnamespace", 6)
                .queryParam("gsrlimit", pageSize)
                .queryParam("gsroffset", (requestedPage - 1) * pageSize)
                .queryParam("prop", "imageinfo")
                .queryParam("iiprop", "url|mime|extmetadata")
                .queryParam("iiurlwidth", 640)
                .queryParam("format", "json")
                .queryParam("formatversion", 2)
                .build().encode().toUriString();
        return resilience.execute("WIKIMEDIA", target, () -> fetchAndNormalize(request, target));
    }

    private JsonNode fetchAndNormalize(AssetSearchRequest request, String target) {
        JsonNode response = client.get().uri(target).retrieve().body(JsonNode.class);
        ObjectNode normalized = objectMapper.createObjectNode();
        ArrayNode results = normalized.putArray("results");
        if (response == null) return normalized;
        for (JsonNode page : response.path("query").path("pages")) {
            JsonNode info = page.path("imageinfo").path(0);
            String mime = info.path("mime").asText();
            if (!matchesType(request.assetType(), mime)) continue;
            JsonNode metadata = info.path("extmetadata");
            String license = clean(metadata.path("LicenseShortName").path("value").asText("unknown"));
            if (Boolean.TRUE.equals(request.commercialUse()) && license.toLowerCase(Locale.ROOT).contains("nc")) continue;
            if (Boolean.TRUE.equals(request.allowModification()) && license.toLowerCase(Locale.ROOT).contains("nd")) continue;
            String originalUrl = info.path("url").asText();
            ObjectNode item = results.addObject();
            item.put("id", page.path("pageid").asText());
            item.put("title", page.path("title").asText("未命名素材").replaceFirst("^File:", ""));
            item.put("creator", clean(metadata.path("Artist").path("value").asText()));
            item.put("foreign_landing_url", info.path("descriptionurl").asText());
            item.put("thumbnail", info.path("thumburl").asText(originalUrl));
            item.put("url", originalUrl);
            item.put("license", license);
            item.put("license_url", metadata.path("LicenseUrl").path("value").asText());
            item.put("attribution", clean(metadata.path("Credit").path("value").asText()));
            item.putArray("tags");
        }
        return normalized;
    }

    private boolean matchesType(String assetType, String mime) {
        if (mime == null) return false;
        return switch (assetType.toUpperCase(Locale.ROOT)) {
            case "VIDEO" -> mime.startsWith("video/");
            case "MEME", "IMAGE" -> mime.startsWith("image/");
            case "SFX", "BGM" -> mime.startsWith("audio/");
            default -> false;
        };
    }

    private String clean(String value) {
        return HtmlUtils.htmlUnescape(value == null ? "" : value.replaceAll("<[^>]+>", " "))
                .replaceAll("\\s+", " ").trim();
    }
}
