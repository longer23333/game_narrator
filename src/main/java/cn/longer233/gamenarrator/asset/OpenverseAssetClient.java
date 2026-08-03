package cn.longer233.gamenarrator.asset;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@Component
public class OpenverseAssetClient {
    private final RestClient client;
    private final boolean enabled;

    public OpenverseAssetClient(@Value("${game-narrator.asset-library.openverse.base-url:https://api.openverse.org/v1}") String baseUrl,
                                @Value("${game-narrator.asset-library.openverse.enabled:true}") boolean enabled,
                                @Value("${game-narrator.asset-library.request-timeout-seconds:6}") int timeoutSeconds) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", "GameNarrator/0.1 graduation-project")
                .build();
        this.enabled = enabled;
    }

    public boolean supports(String assetType) {
        return enabled && !"VIDEO".equalsIgnoreCase(assetType);
    }

    public JsonNode search(AssetSearchRequest request) {
        String endpoint = "MEME".equalsIgnoreCase(request.assetType()) ? "/images/" : "/audio/";
        var uri = UriComponentsBuilder.fromPath(endpoint)
                .queryParam("q", request.query())
                .queryParam("page_size", request.pageSize() == null ? 20 : request.pageSize())
                .queryParam("page", request.page() == null ? 1 : request.page())
                .queryParam("mature", false);
        boolean commercial = Boolean.TRUE.equals(request.commercialUse());
        boolean modification = Boolean.TRUE.equals(request.allowModification());
        if (commercial && modification) {
            uri.queryParam("license", "cc0,pdm,by,by-sa");
        } else if (commercial) {
            uri.queryParam("license_type", "commercial");
        } else if (modification) {
            uri.queryParam("license_type", "modification");
        }
        String target = uri.build().encode().toUriString();
        return client.get().uri(target).retrieve().body(JsonNode.class);
    }
}
