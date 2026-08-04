package cn.longer233.gamenarrator.asset;

import cn.longer233.gamenarrator.importer.RemoteThumbnailService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssetCatalogControllerThumbnailTest {

    @Test
    void returnsLocalSvgPlaceholderWhenPrimaryAndFallbackSourcesFail() {
        AssetCatalogService service = mock(AssetCatalogService.class);
        RemoteThumbnailService thumbnails = mock(RemoteThumbnailService.class);
        UUID id = UUID.randomUUID();
        when(service.remoteThumbnail(id)).thenReturn(new AssetCatalogService.RemoteThumbnailSource(
                "https://i.example/cover.jpg", "https://i.example/fallback.jpg", "https://example/video"));
        when(thumbnails.fetch(anyString(), anyString()))
                .thenThrow(new IllegalStateException("HTTP connect timed out"));
        var controller = new AssetCatalogController(service, mock(AssetLibraryProperties.class),
                mock(SafeRemoteHttpConnector.class), thumbnails);

        var response = controller.thumbnail(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/svg+xml");
        assertThat(response.getHeaders().getFirst("X-GameNarrator-Thumbnail-Fallback")).isEqualTo("true");
        assertThat(new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("远程封面暂不可用");
    }
}
