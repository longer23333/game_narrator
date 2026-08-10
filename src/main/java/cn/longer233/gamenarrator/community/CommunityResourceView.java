package cn.longer233.gamenarrator.community;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CommunityResourceView(UUID id, String resourceType, String code, String name,
        String description, String authorName, String licenseCode, List<String> tags,
        int formatVersion, int installCount, OffsetDateTime publishedAt, OffsetDateTime updatedAt) { }
