package cn.longer233.gamenarrator.asset;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformAssetMetadataResolverTest {
    private final BilibiliAssetClient bilibili = mock(BilibiliAssetClient.class);
    private final PlatformAssetMetadataResolver resolver = new PlatformAssetMetadataResolver(bilibili);

    @Test
    void replacesBilibiliReferenceWithAuthoritativeMetadata() {
        when(bilibili.metadata("BV17e356iEEA")).thenReturn(new BilibiliAssetClient.VideoMetadata(
                "BV17e356iEEA", "正式标题", "作者", "游戏", List.of("游戏", "高光"), 12_000,
                "https://img.test/a.jpg"));

        PlatformAssetMetadataResolver.ResolvedReference result = resolver.resolve("BILIBILI",
                request("添加至稍后再看", "https://www.bilibili.com/video/BV17e356iEEA"));

        assertThat(result.title()).isEqualTo("正式标题");
        assertThat(result.creator()).isEqualTo("作者");
        assertThat(result.previewUrl()).isEqualTo("https://img.test/a.jpg");
        assertThat(result.durationMs()).isEqualTo(12_000);
        assertThat(result.platformTags()).containsExactly("游戏", "高光");
        assertThat(result.fallbackTitleChanged()).isFalse();
    }

    @Test
    void keepsSafeFallbackWhenPlatformLookupFails() {
        when(bilibili.metadata("BV17e356iEEA")).thenThrow(new IllegalStateException("HTTP 412"));

        PlatformAssetMetadataResolver.ResolvedReference result = resolver.resolve("BILIBILI",
                request("添加至稍后再看", "https://www.bilibili.com/video/BV17e356iEEA"));

        assertThat(result.title()).isEqualTo("Bilibili 视频 BV17e356iEEA");
        assertThat(result.platformTags()).containsExactly("原标签");
        assertThat(result.fallbackTitleChanged()).isTrue();
    }

    @Test
    void normalizesOtherProviderTitleWithoutCallingPlatformApi() {
        PlatformAssetMetadataResolver.ResolvedReference result = resolver.resolve("YOUTUBE",
                request("  原始   标题  ", "https://www.youtube.com/watch?v=abc"));

        assertThat(result.title()).isEqualTo("原始 标题");
        assertThat(result.fallbackTitleChanged()).isTrue();
    }

    @Test
    void extractsBvidOnlyFromVideoPath() {
        assertThat(PlatformAssetMetadataResolver.extractBvid(
                "https://www.bilibili.com/video/BV17e356iEEA?p=2")).isEqualTo("BV17e356iEEA");
        assertThat(PlatformAssetMetadataResolver.extractBvid("https://www.bilibili.com/")).isNull();
    }

    private AssetReferenceRequest request(String title, String url) {
        return new AssetReferenceRequest("BILIBILI", url, "https://img.test/fallback.jpg", null,
                title, "旧作者", "VIDEO", "RIGHTS_REVIEW_REQUIRED", null, "需确认权利", List.of("原标签"));
    }
}
