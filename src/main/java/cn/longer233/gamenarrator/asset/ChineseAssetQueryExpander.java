package cn.longer233.gamenarrator.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Converts a Chinese editing intent into terms understood by international open-media indexes.
 * Openverse's q search covers title, description and tags, so a compact English query is used.
 */
@Component
public class ChineseAssetQueryExpander {
    private static final Pattern CHINESE = Pattern.compile("[\\p{IsHan}]");
    private static final Map<String, String> FALLBACK_TERMS = new LinkedHashMap<>();

    static {
        FALLBACK_TERMS.put("\u6b22\u5feb", "happy upbeat cheerful");
        FALLBACK_TERMS.put("\u8f7b\u5feb", "light upbeat cheerful");
        FALLBACK_TERMS.put("\u6fc0\u52b1", "inspiring motivational uplifting");
        FALLBACK_TERMS.put("\u60b2\u4f24", "sad emotional melancholy");
        FALLBACK_TERMS.put("\u7d27\u5f20", "tense suspense dramatic");
        FALLBACK_TERMS.put("\u6d6a\u6f2b", "romantic warm gentle");
        FALLBACK_TERMS.put("冲击", "impact hit boom");
        FALLBACK_TERMS.put("爆炸", "explosion blast boom");
        FALLBACK_TERMS.put("转场", "whoosh swoosh transition");
        FALLBACK_TERMS.put("搞笑", "funny comedy cartoon");
        FALLBACK_TERMS.put("笑声", "laugh laughter comedy");
        FALLBACK_TERMS.put("哭", "crying tears sad emotional");
        FALLBACK_TERMS.put("流泪", "crying tears emotional");
        FALLBACK_TERMS.put("悬疑", "suspense mystery tension");
        FALLBACK_TERMS.put("恐怖", "horror scary dark");
        FALLBACK_TERMS.put("战斗", "battle fight combat");
        FALLBACK_TERMS.put("胜利", "victory win triumph");
        FALLBACK_TERMS.put("失败", "failure lose game over");
        FALLBACK_TERMS.put("热血", "epic energetic heroic");
        FALLBACK_TERMS.put("治愈", "healing calm gentle");
        FALLBACK_TERMS.put("日常", "daily casual peaceful");
        FALLBACK_TERMS.put("环境", "ambient atmosphere background");
        FALLBACK_TERMS.put("电子", "electronic synth game");
        FALLBACK_TERMS.put("钢琴", "piano emotional");
        FALLBACK_TERMS.put("表情包", "reaction meme funny");
        FALLBACK_TERMS.put("震惊", "surprised shocked reaction meme");
        FALLBACK_TERMS.put("猫", "cat reaction meme");
        FALLBACK_TERMS.put("绿幕", "green screen chroma key footage");
    }

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String model;
    private final AssetLibraryProperties.QueryExpansion properties;
    private final Map<String, CachedExpansion> cache = new ConcurrentHashMap<>();

    public ChineseAssetQueryExpander(ObjectMapper objectMapper,
                                     @Value("${game-narrator.ollama.base-url}") String baseUrl,
                                     @Value("${game-narrator.ollama.script-model}") String model,
                                     AssetLibraryProperties libraryProperties) {
        this.objectMapper = objectMapper;
        this.model = model;
        this.properties = libraryProperties.getQueryExpansion();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(properties.getAiTimeoutSeconds());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public AssetSearchExpansion expand(String query, String assetType) {
        String input = query == null ? "" : query.trim();
        if (!CHINESE.matcher(input).find()) {
            return new AssetSearchExpansion(input, input, List.of());
        }
        String cacheKey = String.valueOf(assetType).toUpperCase(Locale.ROOT) + "\n" + input;
        CachedExpansion cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.value();
        AssetSearchExpansion local = dictionaryExpansion(input);
        if (!"game".equals(local.providerQuery())) return local;
        try {
            String prompt = """
                    你是视频剪辑素材搜索词转换器。把中文需求转换成适合开放素材库检索的简短英文关键词。
                    返回 JSON：{"query":"3到6个英文关键词","tags":["2到5个中文标准标签"]}。
                    不要解释，不要添加作品名或具体创作者名。
                    素材类型：%s
                    中文需求：%s
                    """.formatted(assetType, input);
            JsonNode response = client.post().uri("/api/generate").body(Map.of(
                    "model", model, "prompt", prompt, "stream", false, "format", "json",
                    "options", Map.of("temperature", 0.0, "num_predict", 100)
            )).retrieve().body(JsonNode.class);
            JsonNode result = objectMapper.readTree(response.path("response").asText("{}"));
            String providerQuery = result.path("query").asText("").trim();
            List<String> generatedTags = new ArrayList<>();
            result.path("tags").forEach(node -> {
                String tag = clean(node.asText());
                if (!tag.isBlank()) generatedTags.add(tag);
            });
            if (!providerQuery.isBlank() && providerQuery.matches("[\\p{ASCII}\\s]+")) {
                List<String> effectiveTags = generatedTags.isEmpty() ? local.chineseTags() : generatedTags;
                AssetSearchExpansion expansion = new AssetSearchExpansion(input, providerQuery,
                        effectiveTags.stream().distinct().limit(5).toList());
                cache(cacheKey, expansion);
                return expansion;
            }
        } catch (Exception ignored) {
            // Dictionary expansion keeps Chinese search available when Ollama is offline.
        }
        cache(cacheKey, local);
        return local;
    }

    private AssetSearchExpansion dictionaryExpansion(String input) {
        LinkedHashSet<String> english = new LinkedHashSet<>();
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        Map<String, String> termsByIntent = new LinkedHashMap<>();
        for (AssetLibraryProperties.QueryExpansion.Synonym synonym : properties.getSynonyms()) {
            if (synonym.getIntent() != null && synonym.getTerms() != null) {
                termsByIntent.put(synonym.getIntent(), synonym.getTerms());
            }
        }
        if (termsByIntent.isEmpty()) termsByIntent.putAll(FALLBACK_TERMS);
        termsByIntent.forEach((chinese, terms) -> {
            if (input.contains(chinese)) {
                english.addAll(List.of(terms.split(" ")));
                tags.add(chinese);
            }
        });
        if (english.isEmpty()) english.add("game");
        if (tags.isEmpty()) tags.add(clean(input));
        return new AssetSearchExpansion(input, String.join(" ", english), tags.stream().limit(5).toList());
    }

    private void cache(String key, AssetSearchExpansion expansion) {
        if (cache.size() >= properties.getCacheMaxEntries()) {
            cache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(Instant.now()));
            if (cache.size() >= properties.getCacheMaxEntries()) cache.remove(cache.keySet().iterator().next());
        }
        cache.put(key, new CachedExpansion(expansion,
                Instant.now().plus(Duration.ofHours(properties.getCacheHours()))));
    }

    private record CachedExpansion(AssetSearchExpansion value, Instant expiresAt) {}

    private String clean(String value) {
        String result = value == null ? "" : value.trim().replaceAll("[#，,；;]+", "");
        return result.substring(0, Math.min(100, result.length()));
    }
}
