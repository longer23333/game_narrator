package cn.longer233.gamenarrator.asset;

import cn.longer233.gamenarrator.common.ExternalProcessRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AssetDownloadService {
    private static final long MAX_DOWNLOAD_BYTES = 100L * 1024 * 1024;
    private static final Set<String> DOWNLOAD_LICENSES = Set.of(
            "cc0", "pdm", "by", "by-sa", "pexels_license", "pixabay_content_license");

    private final JdbcTemplate jdbc;
    private final SafeRemoteHttpConnector remoteConnector;
    private final Path storageRoot;
    private final String ffmpegCommand;

    public AssetDownloadService(JdbcTemplate jdbc, SafeRemoteHttpConnector remoteConnector,
                                @Value("${game-narrator.storage-root}") String storageRoot,
                                @Value("${game-narrator.ffmpeg-command}") String ffmpegCommand) {
        this.jdbc = jdbc;
        this.remoteConnector = remoteConnector;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.ffmpegCommand = ffmpegCommand;
    }

    @Transactional
    public void download(UUID assetId) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT download_url,license_code FROM external_asset WHERE id=?", assetId);
        String license = String.valueOf(row.get("LICENSE_CODE")).toLowerCase(Locale.ROOT);
        if (!DOWNLOAD_LICENSES.contains(license)) {
            throw new IllegalStateException("该素材许可证不在自动下载白名单中，请在原始页面人工确认");
        }
        URI uri = URI.create(String.valueOf(row.get("DOWNLOAD_URL")));
        validatePublicHttps(uri);
        try {
            Path directory = storageRoot.resolve("library").resolve(assetId.toString()).normalize();
            if (!directory.startsWith(storageRoot)) throw new IllegalStateException("素材目录不在授权存储范围内");
            Files.createDirectories(directory);
            Path output = directory.resolve("source." + extension(uri.getPath())).normalize();
            HttpURLConnection connection = remoteConnector.open(uri, Map.of("User-Agent", "GameNarrator/0.1"), 3);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("素材下载返回 HTTP " + status);
            long length = connection.getContentLengthLong();
            if (length > MAX_DOWNLOAD_BYTES) throw new IllegalStateException("素材超过 100MB 自动下载限制");
            try (InputStream input = connection.getInputStream(); var outputStream = Files.newOutputStream(output)) {
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    total += count;
                    if (total > MAX_DOWNLOAD_BYTES) throw new IllegalStateException("素材超过 100MB 自动下载限制");
                    outputStream.write(buffer, 0, count);
                }
            }
            jdbc.update("UPDATE external_asset SET local_path=?,import_status='DOWNLOADED',downloaded_at=? WHERE id=?",
                    output.toString(), OffsetDateTime.now(), assetId);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("素材下载失败：" + exception.getMessage(), exception);
        }
    }

    @Transactional
    public UUID derive(UUID assetId, AssetDerivativeRequest request) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT asset_type,title,creator,landing_url,license_code,license_url,attribution,
                       duration_ms,local_path,import_status FROM external_asset WHERE id=?
                """, assetId);
        if (!"VIDEO".equals(row.get("ASSET_TYPE"))) throw new IllegalArgumentException("只有视频素材可以提取画面或声音");
        if (!"DOWNLOADED".equals(row.get("IMPORT_STATUS"))) {
            download(assetId);
            row = jdbc.queryForMap("""
                    SELECT asset_type,title,creator,landing_url,license_code,license_url,attribution,
                           duration_ms,local_path,import_status FROM external_asset WHERE id=?
                    """, assetId);
        }
        Path input = Path.of(String.valueOf(row.get("LOCAL_PATH"))).toAbsolutePath().normalize();
        Path directory = storageRoot.resolve("library").resolve(assetId.toString()).normalize();
        if (!input.startsWith(storageRoot) || !directory.startsWith(storageRoot) || !Files.isRegularFile(input)) {
            throw new IllegalStateException("视频文件不在授权素材目录中");
        }
        String mode = request.mode().toUpperCase(Locale.ROOT);
        double timestamp = request.timestampSeconds() == null ? 0 : request.timestampSeconds();
        Path output = directory.resolve("FRAME".equals(mode)
                ? "frame-" + Math.round(timestamp * 1000) + ".jpg" : "audio.m4a").normalize();
        List<String> command = new ArrayList<>(List.of(ffmpegCommand, "-y"));
        if ("FRAME".equals(mode)) command.addAll(List.of("-ss", String.valueOf(timestamp)));
        command.addAll(List.of("-i", input.toString()));
        if ("FRAME".equals(mode)) command.addAll(List.of("-frames:v", "1", "-q:v", "2", output.toString()));
        else command.addAll(List.of("-vn", "-c:a", "aac", "-b:a", "192k", output.toString()));
        try {
            var result = ExternalProcessRunner.run(command, Duration.ofMinutes(10));
            if (result.exitCode() != 0 || !Files.isRegularFile(output)) {
                throw new IllegalStateException("FFmpeg 提取失败：" + conciseOutput(result.output()));
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("无法启动 FFmpeg：" + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("素材提取已中断", exception);
        }
        UUID derivedId = UUID.nameUUIDFromBytes((assetId + ":" + mode + ":" + timestamp)
                .getBytes(StandardCharsets.UTF_8));
        String derivedType = "FRAME".equals(mode) ? "IMAGE" : "SFX";
        String title = row.get("TITLE") + ("FRAME".equals(mode) ? " · 单帧" : " · 音轨");
        cn.longer233.gamenarrator.common.PortableUpsert.update(jdbc, """
                MERGE INTO external_asset(id,provider,external_id,asset_type,title,creator,landing_url,
                preview_url,download_url,license_code,license_url,attribution,duration_ms,local_path,
                import_status,metadata_json,discovered_at,downloaded_at) KEY(provider,external_id)
                VALUES(?,?,?,?,?,?,?,?,NULL,?,?,?,?,?,?,?, ?,?)
                """, "provider,external_id", derivedId, "LOCAL_DERIVED", assetId + ":" + mode + ":" + timestamp, derivedType,
                title, row.get("CREATOR"), row.get("LANDING_URL"), null, row.get("LICENSE_CODE"),
                row.get("LICENSE_URL"), row.get("ATTRIBUTION"), "FRAME".equals(mode) ? null : row.get("DURATION_MS"),
                output.toString(), "DOWNLOADED", "{\"derivedFrom\":\"" + assetId + "\",\"mode\":\"" + mode + "\"}",
                OffsetDateTime.now(), OffsetDateTime.now());
        return derivedId;
    }

    private void validatePublicHttps(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("下载地址必须是公网 HTTPS 地址");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("下载地址不能指向本机或内网");
                }
            }
        } catch (java.net.UnknownHostException exception) {
            throw new IllegalArgumentException("下载地址无法解析", exception);
        }
    }

    private String extension(String path) {
        int dot = path == null ? -1 : path.lastIndexOf('.');
        String value = dot < 0 ? "bin" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
        return value.matches("[a-z0-9]{1,8}") ? value : "bin";
    }

    private String conciseOutput(String output) {
        if (output == null) return "无输出";
        String value = output.strip();
        return value.length() <= 600 ? value : value.substring(value.length() - 600);
    }
}
