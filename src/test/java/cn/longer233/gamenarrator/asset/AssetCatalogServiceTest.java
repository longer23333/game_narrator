package cn.longer233.gamenarrator.asset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssetCatalogServiceTest {
    @Test
    void replacesBilibiliWatchLaterChromeTextWithStableVideoIdentity() {
        String result = AssetCatalogService.cleanReferenceTitle("BILIBILI",
                "添加至稍后再看28.5万52001:55", "https://www.bilibili.com/video/BV1Ab411c7De");

        assertThat(result).isEqualTo("Bilibili 视频 BV1Ab411c7De");
    }

    @Test
    void preservesRealBilibiliVideoTitle() {
        String result = AssetCatalogService.cleanReferenceTitle("BILIBILI",
                "高级弹幕制作教程", "https://www.bilibili.com/video/BV1Ab411c7De");

        assertThat(result).isEqualTo("高级弹幕制作教程");
    }
}
