package cn.longer233.gamenarrator.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;

/** Discovers public Bilibili metadata. Results remain rights-review candidates, never open-license assets. */
@Component
public class BilibiliAssetClient {
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final long minRequestIntervalMs;
    private long nextRequestAtMs;

    public BilibiliAssetClient(ObjectMapper objectMapper,
            @Value("${game-narrator.asset-library.bilibili.base-url:https://api.bilibili.com}") String baseUrl,
            @Value("${game-narrator.asset-library.bilibili.enabled:true}") boolean enabled,
            @Value("${game-narrator.asset-library.bilibili.min-request-interval-ms:800}") long minRequestIntervalMs,
            @Value("${game-narrator.asset-library.request-timeout-seconds:4}") int timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.minRequestIntervalMs = Math.max(0, Math.min(10_000, minRequestIntervalMs));
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(1, Math.min(15, timeoutSeconds)));
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("User-Agent", "Mozilla/5.0 GameNarrator/0.1")
                .defaultHeader("Referer", "https://search.bilibili.com/").build();
    }

    public boolean supports(String assetType) {
        return enabled && java.util.Set.of("VIDEO", "MEME", "SFX", "BGM")
                .contains(String.valueOf(assetType).toUpperCase(java.util.Locale.ROOT));
    }

    public JsonNode search(AssetSearchRequest request) {
        awaitRequestPermit();
        String target = UriComponentsBuilder.fromPath("/x/web-interface/search/type")
                .queryParam("search_type", "video")
                .queryParam("keyword", request.query())
                .queryParam("page", request.page() == null ? 1 : request.page())
                .queryParam("order", order(request.sort()))
                .build().encode().toUriString();
        JsonNode response = client.get().uri(target).retrieve().body(JsonNode.class);
        ObjectNode normalized = objectMapper.createObjectNode();
        ArrayNode results = normalized.putArray("results");
        if (response == null) throw new IllegalStateException("Bilibili search returned an empty response");
        int responseCode = response.path("code").asInt(-1);
        if (responseCode != 0) {
            throw new IllegalStateException("Bilibili search rejected the request: code=" + responseCode);
        }
        int limit = request.pageSize() == null ? 20 : Math.max(1, Math.min(50, request.pageSize()));
        for (JsonNode source : response.path("data").path("result")) {
            if (results.size() >= limit) break;
            if (!relevant(source, request.query())) continue;
            String bvid = source.path("bvid").asText();
            if (bvid.isBlank()) continue;
            ObjectNode item = results.addObject();
            String assetType = String.valueOf(request.assetType()).toUpperCase(java.util.Locale.ROOT);
            item.put("id", bvid + ":" + assetType);
            item.put("title", clean(source.path("title").asText("未命名视频")));
            item.put("creator", clean(source.path("author").asText()));
            item.put("foreign_landing_url", "https://www.bilibili.com/video/" + bvid);
            item.put("thumbnail", https(source.path("pic").asText()));
            item.putNull("url");
            item.put("duration", parseDurationMs(source.path("duration").asText()));
            item.put("license", "RIGHTS_REVIEW_REQUIRED");
            item.putNull("license_url");
            item.put("attribution", candidateLabel(assetType) + "；播放 " + metric(source, "play")
                    + "，弹幕 " + metric(source, "video_review") + "；导入和再创作前必须由用户确认权利");
            ArrayNode tags = item.putArray("tags");
            if ("MEME".equals(assetType)) tags.add("视频封面候选");
            if ("SFX".equals(assetType) || "BGM".equals(assetType)) tags.add("视频音轨候选");
            String category = clean(source.path("typename").asText());
            if (!category.isBlank()) tags.add(category);
            String tagText = source.path("tag").asText();
            if (!tagText.isBlank()) for (String tag : tagText.split(",")) if (!tag.isBlank()) tags.add(tag.trim());
        }
        return normalized;
    }

    private boolean relevant(JsonNode source, String query) {
        String needle = clean(query).toLowerCase(java.util.Locale.ROOT);
        if (needle.isBlank()) return true;
        String haystack = (clean(source.path("title").asText()) + " "
                + clean(source.path("tag").asText()) + " "
                + clean(source.path("typename").asText())).toLowerCase(java.util.Locale.ROOT);
        // One/two-character Chinese searches are especially vulnerable to platform
        // tokenisation noise (for example 狗 matching an unrelated version number).
        if (needle.codePoints().allMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)
                && needle.codePointCount(0, needle.length()) <= 2) return haystack.contains(needle);
        return true;
    }

    public VideoMetadata metadata(String bvid) {
        String value = bvid == null ? "" : bvid.trim();
        if (!value.matches("(?i)BV[0-9A-Za-z]{8,20}")) throw new IllegalArgumentException("Bilibili BV 号无效");
        awaitRequestPermit();
        JsonNode response = client.get().uri("/x/web-interface/view?bvid={bvid}", value)
                .retrieve().body(JsonNode.class);
        requireSuccess(response, "video metadata");
        JsonNode data = response.path("data");
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        String category = clean(data.path("tname").asText());
        if (!category.isBlank()) tags.add(category);
        awaitRequestPermit();
        try {
            JsonNode tagResponse = client.get().uri("/x/tag/archive/tags?bvid={bvid}", value)
                    .retrieve().body(JsonNode.class);
            if (tagResponse != null && tagResponse.path("code").asInt(-1) == 0) {
                tagResponse.path("data").forEach(tag -> {
                    String name = clean(tag.path("tag_name").asText());
                    if (!name.isBlank()) tags.add(name);
                });
            }
        } catch (Exception ignored) {
            // The view endpoint is authoritative enough; tags are optional enrichment.
        }
        return new VideoMetadata(value, clean(data.path("title").asText()),
                clean(data.path("owner").path("name").asText()), category,
                tags.stream().limit(20).toList(), data.path("duration").asLong(0) * 1000,
                https(data.path("pic").asText()));
    }

    private void requireSuccess(JsonNode response, String operation) {
        if (response == null) throw new IllegalStateException("Bilibili " + operation + " returned an empty response");
        int code = response.path("code").asInt(-1);
        if (code != 0) throw new IllegalStateException("Bilibili " + operation + " rejected the request: code=" + code);
    }

    private synchronized void awaitRequestPermit() {
        long waitMs = nextRequestAtMs - System.currentTimeMillis();
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Bilibili search was interrupted", exception);
            }
        }
        nextRequestAtMs = System.currentTimeMillis() + minRequestIntervalMs;
    }

    private long parseDurationMs(String duration) {
        if (duration == null || duration.isBlank()) return 0;
        String[] parts = duration.trim().split(":");
        try {
            long seconds = 0;
            for (String part : parts) seconds = Math.addExact(Math.multiplyExact(seconds, 60), Long.parseLong(part));
            return Math.multiplyExact(seconds, 1000);
        } catch (ArithmeticException | NumberFormatException exception) {
            return 0;
        }
    }

    private String metric(JsonNode source, String field) {
        String value = source.path(field).asText("0").trim();
        return value.isBlank() ? "0" : value;
    }

    private String order(String requestedSort) {
        if (requestedSort == null) return "totalrank";
        return switch (requestedSort.toUpperCase(java.util.Locale.ROOT)) {
            case "NEWEST" -> "pubdate";
            case "POPULAR" -> "click";
            case "DANMAKU" -> "dm";
            default -> "totalrank";
        };
    }

    private String candidateLabel(String assetType) {
        return switch (assetType) {
            case "MEME" -> "Bilibili 视频封面图片候选";
            case "SFX", "BGM" -> "Bilibili 视频音轨候选";
            default -> "Bilibili 公开视频候选素材";
        };
    }

    private String clean(String value) {
        return HtmlUtils.htmlUnescape(value == null ? "" : value.replaceAll("<[^>]+>", " "))
                .replaceAll("\\s+", " ").trim();
    }

    private String https(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.startsWith("//")) return "https:" + value;
        return value.replaceFirst("^http://", "https://");
    }

    public record VideoMetadata(String bvid, String title, String creator, String category,
                                List<String> tags, long durationMs, String thumbnailUrl) { }
}
