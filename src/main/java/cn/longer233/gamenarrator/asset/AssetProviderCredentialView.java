package cn.longer233.gamenarrator.asset;

import java.time.OffsetDateTime;

public record AssetProviderCredentialView(
        String provider,
        boolean configured,
        String source,
        String keyHint,
        OffsetDateTime updatedAt) {
}
