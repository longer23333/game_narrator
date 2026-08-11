package cn.longer233.gamenarrator.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.flywaydb.core.Flyway;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetDownloadServiceTest {
    @TempDir Path storage;

    @Test
    void rejectsDownloadWhenLicenseIsNotOnAutomaticWhitelist() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID id = UUID.randomUUID();
        when(jdbc.queryForMap(startsWith("SELECT download_url,license_code"), eq(id)))
                .thenReturn(Map.of("DOWNLOAD_URL", "https://example.com/video.mp4", "LICENSE_CODE", "ARR"));
        AssetDownloadService service = new AssetDownloadService(
                jdbc, mock(SafeRemoteHttpConnector.class), storage.toString(), "ffmpeg", Runnable::run);

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
                jdbc, mock(SafeRemoteHttpConnector.class), storage.toString(), "missing-ffmpeg", Runnable::run);

        assertThatThrownBy(() -> service.derive(id, new AssetDerivativeRequest("FRAME", 1.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只有视频素材");
    }

    @Test
    void resumesAnInterruptedDownloadWithRangeAndAtomicallyCompletesIt() throws Exception {
        JdbcTemplate jdbc = database();
        UUID id = insertAsset(jdbc, "resume");
        SafeRemoteHttpConnector connector = mock(SafeRemoteHttpConnector.class);
        HttpURLConnection interrupted = connection(200, 10, null, "v1", failingAfter("01234"));
        HttpURLConnection resumed = connection(206, 5, "bytes 5-9/10", "v1",
                new java.io.ByteArrayInputStream("56789".getBytes()));
        when(connector.open(eq(URI.create("https://cdn.example.test/resume.mp4")), any(), eq(3)))
                .thenReturn(interrupted, resumed);
        AssetDownloadService service = new AssetDownloadService(
                jdbc, connector, storage.toString(), "ffmpeg", Runnable::run);

        assertThatThrownBy(() -> service.download(id)).hasMessageContaining("simulated interruption");
        AssetDownloadStatus partial = service.status(id);
        assertThat(partial.status()).isEqualTo("PARTIAL");
        assertThat(partial.downloadedBytes()).isEqualTo(5);
        assertThat(partial.resumable()).isTrue();

        service.download(id);

        AssetDownloadStatus completed = service.status(id);
        assertThat(completed.status()).isEqualTo("DOWNLOADED");
        assertThat(completed.progress()).isEqualTo(100);
        Path output = storage.resolve("library").resolve(id.toString()).resolve("source.mp4");
        assertThat(Files.readString(output)).isEqualTo("0123456789");
        verify(connector).open(eq(URI.create("https://cdn.example.test/resume.mp4")),
                argThat(headers -> "bytes=5-".equals(headers.get("Range"))
                        && "v1".equals(headers.get("If-Range"))), eq(3));
    }

    @Test
    void restartsSafelyWhenServerIgnoresTheRangeHeader() throws Exception {
        JdbcTemplate jdbc = database();
        UUID id = insertAsset(jdbc, "no-range");
        Path directory = storage.resolve("library").resolve(id.toString());
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("source.mp4.part"), "stale");
        jdbc.update("UPDATE external_asset SET download_bytes=5,download_etag='old' WHERE id=?", id);
        SafeRemoteHttpConnector connector = mock(SafeRemoteHttpConnector.class);
        HttpURLConnection full = connection(200, 8, null, "new",
                new java.io.ByteArrayInputStream("new-file".getBytes()));
        when(connector.open(any(), any(), anyInt())).thenReturn(full);
        AssetDownloadService service = new AssetDownloadService(
                jdbc, connector, storage.toString(), "ffmpeg", Runnable::run);

        service.download(id);

        assertThat(Files.readString(directory.resolve("source.mp4"))).isEqualTo("new-file");
        assertThat(service.status(id).downloadedBytes()).isEqualTo(8);
    }

    private JdbcTemplate database() {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:asset-download-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(source).load().migrate();
        return new JdbcTemplate(source);
    }

    private UUID insertAsset(JdbcTemplate jdbc, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO external_asset(id,provider,external_id,asset_type,title,landing_url,download_url,
                license_code,import_status,metadata_json,discovered_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """, id, "PEXELS", name, "VIDEO", name, "https://example.test/item/" + name,
                "https://cdn.example.test/" + name + ".mp4", "cc0", "DISCOVERED", "{}", OffsetDateTime.now());
        return id;
    }

    private HttpURLConnection connection(int status, long length, String contentRange,
                                         String etag, InputStream input) throws Exception {
        HttpURLConnection connection = mock(HttpURLConnection.class);
        when(connection.getResponseCode()).thenReturn(status);
        when(connection.getContentLengthLong()).thenReturn(length);
        when(connection.getHeaderField("Content-Range")).thenReturn(contentRange);
        when(connection.getHeaderField("ETag")).thenReturn(etag);
        when(connection.getHeaderField("Last-Modified")).thenReturn(null);
        when(connection.getInputStream()).thenReturn(input);
        return connection;
    }

    private InputStream failingAfter(String prefix) {
        byte[] bytes = prefix.getBytes();
        return new InputStream() {
            private boolean delivered;
            @Override public int read() throws IOException { throw new UnsupportedOperationException(); }
            @Override public int read(byte[] target, int offset, int length) throws IOException {
                if (delivered) throw new IOException("simulated interruption");
                delivered = true;
                System.arraycopy(bytes, 0, target, offset, bytes.length);
                return bytes.length;
            }
        };
    }
}
