package cn.longer233.gamenarrator.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssetDownloadServiceTest {
    @TempDir Path storage;

    @Test
    void rejectsDownloadWhenLicenseIsNotOnAutomaticWhitelist() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID id = UUID.randomUUID();
        when(jdbc.queryForMap("SELECT download_url,license_code FROM external_asset WHERE id=?", id))
                .thenReturn(Map.of("DOWNLOAD_URL", "https://example.com/video.mp4", "LICENSE_CODE", "ARR"));
        AssetDownloadService service = new AssetDownloadService(
                jdbc, mock(SafeRemoteHttpConnector.class), storage.toString(), "ffmpeg");

        assertThatThrownBy(() -> service.download(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("许可证");
    }

    @Test
    void rejectsDerivativeForNonVideoAssetBeforeStartingFfmpeg() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID id = UUID.randomUUID();
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.startsWith("SELECT asset_type"),
                org.mockito.ArgumentMatchers.eq(id))).thenReturn(Map.of("ASSET_TYPE", "IMAGE"));
        AssetDownloadService service = new AssetDownloadService(
                jdbc, mock(SafeRemoteHttpConnector.class), storage.toString(), "missing-ffmpeg");

        assertThatThrownBy(() -> service.derive(id, new AssetDerivativeRequest("FRAME", 1.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只有视频素材");
    }
}
