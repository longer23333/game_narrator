package cn.longer233.gamenarrator.asset;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AssetView(
        UUID id,
        String provider,
        String assetType,
        String title,
        String localizedTitle,
        String creator,
        String landingUrl,
        String previewUrl,
        String downloadUrl,
        String licenseCode,
        String licenseUrl,
        String attribution,
        Long durationMs,
        String importStatus,
        String localPath,
        boolean favorite,
        boolean archived,
        List<TagView> tags,
        OffsetDateTime discoveredAt
) {
    public record TagView(String name, List<String> sources, boolean userAdded) {}
}
