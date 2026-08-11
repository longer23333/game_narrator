package cn.longer233.gamenarrator.asset;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AssetDownloadStatus(
        UUID assetId,
        String status,
        long downloadedBytes,
        Long totalBytes,
        int progress,
        boolean resumable,
        String error,
        OffsetDateTime startedAt) {
}
