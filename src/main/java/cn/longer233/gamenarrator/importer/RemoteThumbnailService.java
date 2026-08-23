package cn.longer233.gamenarrator.importer;

import cn.longer233.gamenarrator.asset.SafeRemoteHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class RemoteThumbnailService {
    private final int maxBytes;
    private final ThumbnailMemoryCache cache;
    private final SafeRemoteHttpConnector connector;
    private final RemoteThumbnailFetchPolicy fetchPolicy;

    public RemoteThumbnailService(
            @Value("${game-narrator.media-preview.thumbnail-max-bytes:1572864}") int maxBytes,
            @Value("${game-narrator.media-preview.thumbnail-cache-entries:96}") int maxCacheEntries,
            @Value("${game-narrator.media-preview.thumbnail-cache-minutes:10}") int cacheMinutes,
            @Value("${game-narrator.media-preview.thumbnail-max-concurrent:4}") int maxConcurrent,
            @Value("${game-narrator.media-preview.thumbnail-acquire-timeout-ms:500}") long acquireTimeoutMs,
            @Value("${game-narrator.media-preview.thumbnail-failure-cache-seconds:90}") int failureCacheSeconds) {
        this.maxBytes = Math.max(128 * 1024, Math.min(5 * 1024 * 1024, maxBytes));
        int cacheEntries = Math.max(4, Math.min(512, maxCacheEntries));
        Duration cacheTtl = Duration.ofMinutes(Math.max(1, Math.min(60, cacheMinutes)));
        this.cache = new ThumbnailMemoryCache(cacheEntries, cacheTtl);
        this.connector = new SafeRemoteHttpConnector();
        this.fetchPolicy = new RemoteThumbnailFetchPolicy(maxConcurrent, acquireTimeoutMs,
                cacheEntries * 4, Duration.ofSeconds(Math.max(5, Math.min(600, failureCacheSeconds))));
    }

    public ThumbnailContent fetch(String thumbnailUrl, String sourceUrl) {
        try {
            URI uri = requirePublicHttps(thumbnailUrl);
            String cacheKey = uri + "\n" + (sourceUrl == null ? "" : sourceUrl);
            return cache.get(cacheKey, () -> fetchUncached(uri, sourceUrl, cacheKey));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取视频封面：" + exception.getMessage(), exception);
        }
    }

    private ThumbnailContent fetchUncached(URI uri, String sourceUrl, String cacheKey) {
        try (var ignored = fetchPolicy.acquire(cacheKey)) {
                try {
                    HttpURLConnection response = fetchFollowingRedirects(uri, sourceUrl);
                    int status=response.getResponseCode();
                    if (status < 200 || status >= 300) {
                        response.disconnect();
                        throw new IllegalStateException("封面源返回 HTTP " + status);
                    }
                    String rawContentType=response.getHeaderField("Content-Type");
                    String contentType = (rawContentType == null ? "application/octet-stream" : rawContentType)
                            .split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
                    if (!contentType.startsWith("image/")) {
                        response.disconnect();
                        throw new IllegalStateException("封面源返回的不是图片");
                    }
                    try (InputStream input = response.getInputStream()) {
                        byte[] bytes = input.readNBytes(maxBytes + 1);
                        if (bytes.length > maxBytes) throw new IllegalStateException("封面图片超过缓存大小限制");
                        ThumbnailContent content = new ThumbnailContent(contentType, bytes);
                        fetchPolicy.succeeded(cacheKey);
                        return content;
                    } finally {
                        response.disconnect();
                    }
                } catch (RuntimeException exception) {
                    fetchPolicy.failed(cacheKey, exception);
                    throw exception;
                } catch (Exception exception) {
                    IllegalStateException wrapped = new IllegalStateException("无法读取视频封面：" + exception.getMessage(), exception);
                    fetchPolicy.failed(cacheKey, wrapped);
                    throw wrapped;
                }
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取视频封面：" + exception.getMessage(), exception);
        }
    }

    private HttpURLConnection fetchFollowingRedirects(URI initial, String sourceUrl) throws Exception {
        Map<String,String> headers=new LinkedHashMap<>();
        headers.put("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 GameNarrator/1.0");
        headers.put("Accept","image/avif,image/webp,image/png,image/jpeg,image/*;q=0.8");
        if (sourceUrl != null && !sourceUrl.isBlank()) headers.put("Referer",sourceUrl);
        return connector.open(initial,headers,3);
    }

    private URI requirePublicHttps(String value) throws Exception {
        URI uri = upgradeToHttps(URI.create(value));
        connector.validatePublicHttps(uri);
        return uri;
    }

    static URI upgradeToHttps(URI uri) {
        if (!"http".equalsIgnoreCase(uri.getScheme())) return uri;
        try {
            return new URI("https", uri.getUserInfo(), uri.getHost(), uri.getPort(),
                    uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalArgumentException("封面地址格式无效", exception);
        }
    }

    public record ThumbnailContent(String contentType, byte[] bytes) { }
}
