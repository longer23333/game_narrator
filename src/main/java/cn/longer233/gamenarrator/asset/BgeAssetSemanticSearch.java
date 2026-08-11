package cn.longer233.gamenarrator.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

@Component
public class BgeAssetSemanticSearch {
    private static final Logger log = LoggerFactory.getLogger(BgeAssetSemanticSearch.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RestClient client;
    private final boolean enabled;
    private final String model;
    private final double minimumScore;
    private final int resultLimit;
    private volatile Instant unavailableUntil = Instant.EPOCH;

    public BgeAssetSemanticSearch(JdbcTemplate jdbc, ObjectMapper objectMapper,
            @Value("${game-narrator.asset-library.semantic-search.enabled:true}") boolean enabled,
            @Value("${game-narrator.asset-library.semantic-search.model:bge-m3}") String model,
            @Value("${game-narrator.asset-library.semantic-search.timeout-seconds:8}") int timeoutSeconds,
            @Value("${game-narrator.asset-library.semantic-search.minimum-score:0.28}") double minimumScore,
            @Value("${game-narrator.asset-library.semantic-search.result-limit:60}") int resultLimit,
            @Value("${game-narrator.ollama.base-url:http://localhost:11434}") String baseUrl) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.model = model;
        this.minimumScore = minimumScore;
        this.resultLimit = Math.max(1, Math.min(100, resultLimit));
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(1, Math.min(30, timeoutSeconds)));
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public List<AssetView> rerank(String query, List<AssetView> candidates) {
        if (!enabled || query == null || query.isBlank() || candidates.isEmpty()
                || unavailableUntil.isAfter(Instant.now())) return List.of();
        try {
            List<String> texts = candidates.stream().map(this::semanticText).toList();
            List<double[]> vectors = new ArrayList<>(candidates.size());
            List<Integer> missing = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                double[] cached = cached(candidates.get(i).id(), hash(texts.get(i)));
                vectors.add(cached);
                if (cached == null) missing.add(i);
            }
            List<String> inputs = new ArrayList<>();
            inputs.add(query.trim());
            for (int index : missing) inputs.add(texts.get(index));
            List<double[]> generated = embedTexts(inputs);
            if (generated.size() != inputs.size()) throw new IllegalStateException("BGE returned an unexpected vector count");
            for (int i = 0; i < missing.size(); i++) {
                int candidateIndex = missing.get(i);
                double[] vector = generated.get(i + 1);
                vectors.set(candidateIndex, vector);
                save(candidates.get(candidateIndex).id(), hash(texts.get(candidateIndex)), vector);
            }
            double[] queryVector = generated.getFirst();
            List<ScoredAsset> scored = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                double semanticScore = cosine(queryVector, vectors.get(i));
                double lexicalScore = lexicalScore(query, semanticText(candidates.get(i)));
                double score = Math.min(1, semanticScore * .78 + lexicalScore * .22);
                if (lexicalScore > 0 || semanticScore >= minimumScore) scored.add(new ScoredAsset(candidates.get(i), score));
            }
            scored.sort(Comparator.comparingDouble(ScoredAsset::score).reversed());
            return scored.stream().limit(resultLimit).map(ScoredAsset::asset).toList();
        } catch (Exception exception) {
            unavailableUntil = Instant.now().plus(Duration.ofMinutes(1));
            log.warn("BGE semantic search unavailable; lexical search will be used: {}", exception.getMessage());
            return List.of();
        }
    }

    public String model() { return model; }
    public boolean configured() { return enabled; }
    public List<AssetView> similarTo(AssetView anchor, List<AssetView> candidates) {
        if (anchor == null) return List.of();
        return rerank(semanticText(anchor), candidates.stream()
                .filter(candidate -> !candidate.id().equals(anchor.id()))
                .toList());
    }
    public boolean available() {
        if (!enabled) return false;
        try {
            JsonNode response = client.post().uri("/api/show")
                    .body(Map.of("model", model)).retrieve().body(JsonNode.class);
            return response != null && !response.isEmpty();
        } catch (Exception exception) {
            return false;
        }
    }

    public List<double[]> embedTexts(List<String> input) throws Exception {
        JsonNode response = client.post().uri("/api/embed")
                .body(Map.of("model", model, "input", input)).retrieve().body(JsonNode.class);
        List<double[]> result = new ArrayList<>();
        if (response != null) for (JsonNode embedding : response.path("embeddings")) {
            double[] vector = new double[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) vector[i] = embedding.get(i).asDouble();
            result.add(vector);
        }
        return result;
    }

    public double similarity(double[] left, double[] right) { return cosine(left, right); }

    private double[] cached(UUID assetId, String contentHash) {
        List<String> rows = jdbc.query("SELECT vector_json FROM asset_embedding WHERE asset_id=? AND model=? AND content_hash=?",
                (rs, n) -> rs.getString(1), assetId, model, contentHash);
        if (rows.isEmpty()) return null;
        try { return objectMapper.readValue(rows.getFirst(), double[].class); }
        catch (Exception exception) { return null; }
    }

    private void save(UUID assetId, String contentHash, double[] vector) throws Exception {
        cn.longer233.gamenarrator.common.PortableUpsert.update(jdbc, """
                MERGE INTO asset_embedding(asset_id,model,dimensions,content_hash,vector_json,embedded_at)
                KEY(asset_id) VALUES(?,?,?,?,?,?)
                """, "asset_id", assetId, model, vector.length, contentHash,
                objectMapper.writeValueAsString(vector), OffsetDateTime.now());
    }

    private String semanticText(AssetView asset) {
        String tags = asset.tags().stream().map(AssetView.TagView::name).reduce("", (a, b) -> a + " " + b);
        return (asset.assetType() + " " + asset.title() + " " + Objects.toString(asset.localizedTitle(), "")
                + " " + Objects.toString(asset.creator(), "") + tags).trim();
    }

    private String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private double cosine(double[] left, double[] right) {
        if (left == null || right == null || left.length != right.length || left.length == 0) return -1;
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i]; leftNorm += left[i] * left[i]; rightNorm += right[i] * right[i];
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm) + 1e-12);
    }

    static double lexicalScore(String query, String candidate) {
        String text = Objects.toString(candidate, "").toLowerCase(Locale.ROOT);
        String normalized = Objects.toString(query, "").toLowerCase(Locale.ROOT).trim();
        if (normalized.isEmpty()) return 0;
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String term : normalized.split("[\\s,，;；]+")) if (term.length() >= 2) terms.add(term);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[\\p{IsHan}]{2,}").matcher(normalized);
        while (matcher.find()) {
            String chinese = matcher.group(); terms.add(chinese);
            if (chinese.length() > 2) for (int i = 0; i < chinese.length() - 1; i++) terms.add(chinese.substring(i, i + 2));
        }
        if (terms.isEmpty()) return text.contains(normalized) ? 1 : 0;
        long hits = terms.stream().filter(text::contains).count();
        double coverage = (double) hits / terms.size();
        if (text.contains(normalized)) coverage = Math.min(1, coverage + .25);
        return coverage;
    }

    private record ScoredAsset(AssetView asset, double score) {}
}
