package cn.longer233.gamenarrator.asset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves platform-owned metadata without coupling catalog persistence to provider APIs. */
@Component
public class PlatformAssetMetadataResolver {
    private static final Logger log = LoggerFactory.getLogger(PlatformAssetMetadataResolver.class);
    private static final Pattern BILIBILI_VIDEO = Pattern.compile("(?i)/video/(BV[0-9A-Za-z]+)");
    private final BilibiliAssetClient bilibili;

    public PlatformAssetMetadataResolver(BilibiliAssetClient bilibili) {
        this.bilibili = bilibili;
    }

    public ResolvedReference resolve(String provider, AssetReferenceRequest request) {
        String normalizedProvider = String.valueOf(provider).toUpperCase(Locale.ROOT);
        String title = cleanReferenceTitle(normalizedProvider, request.title(), request.sourceUrl());
        boolean fallbackTitleChanged = !title.equals(request.title().trim());
        ResolvedReference fallback = new ResolvedReference(title, request.creator(), request.previewUrl(), null,
                request.platformTags() == null ? List.of() : List.copyOf(request.platformTags()), fallbackTitleChanged);
        if (!"BILIBILI".equals(normalizedProvider)) return fallback;
        return metadata(request.sourceUrl())
                .map(value -> new ResolvedReference(value.title(), value.creator(), value.thumbnailUrl(),
                        value.durationMs(), value.tags(), false))
                .orElse(fallback);
    }

    public Optional<BilibiliAssetClient.VideoMetadata> metadata(String sourceUrl) {
        String bvid = extractBvid(sourceUrl);
        if (bvid == null) return Optional.empty();
        try {
            return Optional.of(bilibili.metadata(bvid));
        } catch (Exception exception) {
            log.warn("Bilibili metadata lookup failed bvid={}: {}", bvid, concise(exception));
            return Optional.empty();
        }
    }

    static String cleanReferenceTitle(String provider, String rawTitle, String sourceUrl) {
        String title = rawTitle == null ? "" : rawTitle.replaceAll("\\s+", " ").trim();
        if (!"BILIBILI".equals(provider)) return title;
        boolean interfaceText = title.matches("(?i)^(?:添加至)?稍后再看.*")
                || title.matches("^[\\d.]+(?:万|亿)?\\s*[\\d.]+(?:万|亿)?\\s*\\d{1,2}:\\d{2}$");
        if (!interfaceText) return title;
        String bvid = extractBvid(sourceUrl);
        return bvid == null ? "Bilibili 视频候选素材" : "Bilibili 视频 " + bvid;
    }

    static String extractBvid(String sourceUrl) {
        Matcher matcher = BILIBILI_VIDEO.matcher(sourceUrl == null ? "" : sourceUrl);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String concise(Exception exception) {
        String message = exception.getMessage();
        if (message == null) return exception.getClass().getSimpleName();
        if (message.contains("412")) return "HTTP 412 platform risk control";
        return message.length() <= 240 ? message : message.substring(0, 240) + "…";
    }

    public record ResolvedReference(String title, String creator, String previewUrl, Long durationMs,
                                    List<String> platformTags, boolean fallbackTitleChanged) { }
}
