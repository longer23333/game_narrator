package cn.longer233.gamenarrator.asset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssetCatalogServiceTest {
    @Test
    void cachedAssetsRespectCommercialAndModificationConditions() {
        var request = new AssetSearchRequest("test", "IMAGE", 10, 1, true, true, null, "RELEVANCE");
        assertThat(AssetCatalogService.matchesLicenseConditions("CC BY-NC 4.0", request)).isFalse();
        assertThat(AssetCatalogService.matchesLicenseConditions("CC BY-ND 4.0", request)).isFalse();
        assertThat(AssetCatalogService.matchesLicenseConditions("CC BY-SA 4.0", request)).isTrue();
        var unrestricted = new AssetSearchRequest("test", "IMAGE", 10, 1, false, false, null, "RELEVANCE");
        assertThat(AssetCatalogService.matchesLicenseConditions("CC BY-NC-ND 4.0", unrestricted)).isTrue();
    }

    @Test
    void replacesBilibiliWatchLaterChromeTextWithStableVideoIdentity() {
        String result = PlatformAssetMetadataResolver.cleanReferenceTitle("BILIBILI",
                "添加至稍后再看28.5万52001:55", "https://www.bilibili.com/video/BV1Ab411c7De");

        assertThat(result).isEqualTo("Bilibili 视频 BV1Ab411c7De");
    }

    @Test
    void preservesRealBilibiliVideoTitle() {
        String result = PlatformAssetMetadataResolver.cleanReferenceTitle("BILIBILI",
                "高级弹幕制作教程", "https://www.bilibili.com/video/BV1Ab411c7De");

        assertThat(result).isEqualTo("高级弹幕制作教程");
    }
}
