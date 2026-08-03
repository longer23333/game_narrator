package cn.longer233.gamenarrator.importer;

import java.util.List;

public record ResolvedMedia(
        String platform,
        String sourceId,
        String title,
        String creator,
        String thumbnail,
        String thumbnailPreviewUrl,
        Double durationSeconds,
        List<String> tags,
        List<MediaVariant> variants,
        String contentOrigin,
        String contentOriginLabel,
        Double originConfidence,
        String originReason
) {
    public ResolvedMedia(String platform, String sourceId, String title, String creator,
                         String thumbnail, String thumbnailPreviewUrl, Double durationSeconds,
                         List<String> tags, List<MediaVariant> variants) {
        this(platform, sourceId, title, creator, thumbnail, thumbnailPreviewUrl, durationSeconds,
                tags, variants, "UNKNOWN", "来源性质未知", 0.0, "尚未评估");
    }
}
