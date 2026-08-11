package cn.longer233.gamenarrator.importer;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

@Service
public class RemoteThumbnailService {
    private final int maxBytes;
    private final ThumbnailMemoryCache cache;
    private final HttpClient client;
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
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
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
                    HttpResponse<InputStream> response = fetchFollowingRedirects(uri, sourceUrl);
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        response.body().close();
                        throw new IllegalStateException("封面源返回 HTTP " + response.statusCode());
                    }
                    String contentType = response.headers().firstValue("Content-Type")
                            .orElse("application/octet-stream").split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
                    if (!contentType.startsWith("image/")) {
                        response.body().close();
                        throw new IllegalStateException("封面源返回的不是图片");
                    }
                    try (InputStream input = response.body()) {
                        byte[] bytes = input.readNBytes(maxBytes + 1);
                        if (bytes.length > maxBytes) throw new IllegalStateException("封面图片超过缓存大小限制");
                        ThumbnailContent content = new ThumbnailContent(contentType, bytes);
                        fetchPolicy.succeeded(cacheKey);
                        return content;
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

    private HttpResponse<InputStream> fetchFollowingRedirects(URI initial, String sourceUrl) throws Exception {
        URI current = initial;
        for (int redirect = 0; redirect <= 3; redirect++) {
            current = requirePublicHttps(current.toString());
            HttpRequest.Builder request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 GameNarrator/1.0")
                    .header("Accept", "image/avif,image/webp,image/png,image/jpeg,image/*;q=0.8");
            if (sourceUrl != null && !sourceUrl.isBlank()) request.header("Referer", sourceUrl);
            HttpResponse<InputStream> response = client.send(request.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status < 300 || status >= 400) return response;
            String location = response.headers().firstValue("Location")
                    .orElseThrow(() -> new IllegalStateException("封面跳转缺少 Location"));
            response.body().close();
            current = current.resolve(location);
        }
        throw new IllegalStateException("封面跳转次数过多");
    }

    private URI requirePublicHttps(String value) throws Exception {
        URI uri = upgradeToHttps(URI.create(value));
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("封面地址必须是 HTTPS 公网地址");
        }
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                throw new IllegalArgumentException("禁止代理本地或内网封面地址");
            }
        }
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
