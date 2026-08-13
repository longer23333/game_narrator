package cn.longer233.gamenarrator.mobile;

/** Validates the provenance that must be shown before a public asset is downloaded. */
public final class PublicAssetRightsPolicy {
    private PublicAssetRightsPolicy() { }

    public static void requireDownloadable(PublicAsset asset, boolean sourceReviewed,
                                           boolean licenseConfirmed, boolean uploaderRightsConfirmed) {
        if (asset == null || asset.provider().isBlank() || asset.pageUrl().isBlank()
                || asset.directUrl().isBlank() || asset.license().isBlank()) {
            throw new IllegalArgumentException("素材缺少来源页、下载地址或许可信息");
        }
        if (!sourceReviewed || !licenseConfirmed) {
            throw new IllegalStateException("请先查看来源页并确认许可范围");
        }
        if ("BILIBILI".equalsIgnoreCase(asset.provider()) && !uploaderRightsConfirmed) {
            throw new IllegalStateException("Bilibili 候选素材必须确认上传者授权或合理使用依据");
        }
    }
}
