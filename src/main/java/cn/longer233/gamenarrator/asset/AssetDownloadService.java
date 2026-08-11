package cn.longer233.gamenarrator.asset;

import cn.longer233.gamenarrator.common.ExternalProcessRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class AssetDownloadService {
    private static final long MAX_DOWNLOAD_BYTES = 100L * 1024 * 1024;
    private static final Set<String> DOWNLOAD_LICENSES = Set.of(
            "cc0", "pdm", "by", "by-sa", "pexels_license", "pixabay_content_license");

    private final JdbcTemplate jdbc;
    private final SafeRemoteHttpConnector remoteConnector;
    private final Path storageRoot;
    private final String ffmpegCommand;
    private final Executor taskExecutor;
    private final Set<UUID> activeDownloads = ConcurrentHashMap.newKeySet();

    public AssetDownloadService(JdbcTemplate jdbc, SafeRemoteHttpConnector remoteConnector,
                                @Value("${game-narrator.storage-root}") String storageRoot,
                                @Value("${game-narrator.ffmpeg-command}") String ffmpegCommand,
                                @Qualifier("taskExecutor") Executor taskExecutor) {
        this.jdbc = jdbc;
        this.remoteConnector = remoteConnector;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.ffmpegCommand = ffmpegCommand;
        this.taskExecutor = taskExecutor;
    }

    public AssetDownloadStatus enqueue(UUID assetId) {
        validateDownloadRequest(assetId);
        if (activeDownloads.add(assetId)) {
            jdbc.update("""
                    UPDATE external_asset SET import_status='QUEUED',download_error=NULL,download_started_at=? WHERE id=?
                    """, OffsetDateTime.now(), assetId);
            try {
                taskExecutor.execute(() -> {
                    try { downloadInternal(assetId); }
                    finally { activeDownloads.remove(assetId); }
                });
            } catch (RuntimeException failure) {
                activeDownloads.remove(assetId);
                recordFailure(assetId, failure);
                throw failure;
            }
        }
        return status(assetId);
    }

    public void download(UUID assetId) {
        if (!activeDownloads.add(assetId)) {
            throw new IllegalStateException("该素材正在下载，请等待完成后再提取或重试");
        }
        try { downloadInternal(assetId); }
        finally { activeDownloads.remove(assetId); }
    }

    private void downloadInternal(UUID assetId) {
        Map<String, Object> row = validateDownloadRequest(assetId);
        URI uri = URI.create(String.valueOf(row.get("DOWNLOAD_URL")));
        try {
            Path directory = storageRoot.resolve("library").resolve(assetId.toString()).normalize();
            if (!directory.startsWith(storageRoot)) throw new IllegalStateException("素材目录不在授权存储范围内");
            Files.createDirectories(directory);
            Path output = directory.resolve("source." + extension(uri.getPath())).normalize();
            Path partial = directory.resolve(output.getFileName() + ".part").normalize();
            long existing = Files.isRegularFile(partial) ? Files.size(partial) : 0;
            if (existing > MAX_DOWNLOAD_BYTES) {
                Files.deleteIfExists(partial);
                existing = 0;
            }
            jdbc.update("""
                    UPDATE external_asset SET import_status='DOWNLOADING',download_bytes=?,download_error=NULL,
                    download_started_at=COALESCE(download_started_at,?) WHERE id=?
                    """, existing, OffsetDateTime.now(), assetId);
            transfer(assetId, uri, partial, output, existing, text(row.get("DOWNLOAD_ETAG")),
                    text(row.get("DOWNLOAD_LAST_MODIFIED")));
        } catch (Exception failure) {
            recordFailure(assetId, failure);
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("素材下载失败：" + failure.getMessage(), failure);
        }
    }

    public AssetDownloadStatus status(UUID assetId) {
        return jdbc.queryForObject("""
                SELECT id,import_status,download_bytes,download_total_bytes,download_error,download_started_at
                FROM external_asset WHERE id=?
                """, (rs, row) -> {
            long downloaded = rs.getLong("download_bytes");
            Long total = rs.getObject("download_total_bytes", Long.class);
            int progress = total == null || total <= 0 ? 0 : (int) Math.min(100, downloaded * 100 / total);
            String state = rs.getString("import_status");
            return new AssetDownloadStatus(rs.getObject("id", UUID.class), state, downloaded, total,
                    "DOWNLOADED".equals(state) ? 100 : progress,
                    downloaded > 0 && !"DOWNLOADED".equals(state), rs.getString("download_error"),
                    rs.getObject("download_started_at", OffsetDateTime.class));
        }, assetId);
    }

    private Map<String, Object> validateDownloadRequest(UUID assetId) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT download_url,license_code,download_etag,download_last_modified FROM external_asset WHERE id=?",
                assetId);
        String license = String.valueOf(row.get("LICENSE_CODE")).toLowerCase(Locale.ROOT);
        if (!DOWNLOAD_LICENSES.contains(license)) {
            throw new IllegalStateException("该素材许可证不在自动下载白名单中，请在原始页面人工确认");
        }
        URI uri = URI.create(String.valueOf(row.get("DOWNLOAD_URL")));
        remoteConnector.validatePublicHttps(uri);
        return row;
    }

    private void transfer(UUID assetId, URI uri, Path partial, Path output, long existing,
                          String storedEtag, String storedLastModified) throws Exception {
        long offset = existing;
        for (int requestNo = 0; requestNo < 2; requestNo++) {
            Map<String, String> headers = new java.util.LinkedHashMap<>();
            headers.put("User-Agent", "GameNarrator/0.1");
            if (offset > 0) {
                headers.put("Range", "bytes=" + offset + "-");
                String validator = !storedEtag.isBlank() ? storedEtag : storedLastModified;
                if (!validator.isBlank()) headers.put("If-Range", validator);
            }
            HttpURLConnection connection = remoteConnector.open(uri, headers, 3);
            try {
                int response = connection.getResponseCode();
                if (response == 416 && offset > 0) {
                    Long total = unsatisfiedTotal(connection.getHeaderField("Content-Range"));
                    if (total != null && total == offset) {
                        finish(assetId, partial, output, offset, total);
                        return;
                    }
                    Files.deleteIfExists(partial);
                    offset = 0;
                    continue;
                }
                if (response != HttpURLConnection.HTTP_OK && response != HttpURLConnection.HTTP_PARTIAL) {
                    throw new IllegalStateException("素材下载返回 HTTP " + response);
                }
                boolean append = offset > 0 && response == HttpURLConnection.HTTP_PARTIAL;
                if (append && !validContentRange(connection.getHeaderField("Content-Range"), offset)) {
                    throw new IllegalStateException("远程服务器返回了不匹配的 Content-Range，已保留断点等待重试");
                }
                if (!append) offset = 0;
                Long total = responseTotal(connection, offset);
                if (total != null && total > MAX_DOWNLOAD_BYTES) throw new IllegalStateException("素材超过 100MB 自动下载限制");
                String etag = text(connection.getHeaderField("ETag"));
                String lastModified = text(connection.getHeaderField("Last-Modified"));
                jdbc.update("""
                        UPDATE external_asset SET download_total_bytes=?,download_etag=?,download_last_modified=? WHERE id=?
                        """, total, blankToNull(etag), blankToNull(lastModified), assetId);
                long written = stream(assetId, connection.getInputStream(), partial, offset, append, total);
                finish(assetId, partial, output, written, total);
                return;
            } finally { connection.disconnect(); }
        }
        throw new IllegalStateException("远程断点已失效，重新下载初始化失败");
    }

    private long stream(UUID assetId, InputStream input, Path partial, long offset,
                        boolean append, Long expectedTotal) throws Exception {
        StandardOpenOption[] options = append
                ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND}
                : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};
        long total = offset;
        long lastReported = offset;
        try (input; var output = Files.newOutputStream(partial, options)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                total += count;
                if (total > MAX_DOWNLOAD_BYTES) throw new IllegalStateException("素材超过 100MB 自动下载限制");
                output.write(buffer, 0, count);
                if (total - lastReported >= 512 * 1024) {
                    jdbc.update("UPDATE external_asset SET download_bytes=? WHERE id=?", total, assetId);
                    lastReported = total;
                }
            }
            output.flush();
        }
        if (expectedTotal != null && total != expectedTotal) {
            throw new IllegalStateException("远程素材未下载完整：已收到 " + total + " / " + expectedTotal + " 字节");
        }
        jdbc.update("UPDATE external_asset SET download_bytes=? WHERE id=?", total, assetId);
        return total;
    }

    private void finish(UUID assetId, Path partial, Path output, long bytes, Long expectedTotal) throws Exception {
        if (!Files.isRegularFile(partial) || Files.size(partial) != bytes || bytes <= 0) {
            throw new IllegalStateException("下载临时文件不完整，不能写入素材库");
        }
        if (expectedTotal != null && bytes != expectedTotal) throw new IllegalStateException("下载文件大小校验失败");
        try { Files.move(partial, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(partial, output, StandardCopyOption.REPLACE_EXISTING);
        }
        jdbc.update("""
                UPDATE external_asset SET local_path=?,import_status='DOWNLOADED',download_bytes=?,
                download_total_bytes=?,download_error=NULL,downloaded_at=? WHERE id=?
                """, output.toString(), bytes, expectedTotal == null ? bytes : expectedTotal,
                OffsetDateTime.now(), assetId);
    }

    private void recordFailure(UUID assetId, Throwable failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        long bytes = 0;
        try {
            URI uri = URI.create(String.valueOf(jdbc.queryForMap("SELECT download_url FROM external_asset WHERE id=?", assetId)
                    .get("DOWNLOAD_URL")));
            Path partial = storageRoot.resolve("library").resolve(assetId.toString())
                    .resolve("source." + extension(uri.getPath()) + ".part").normalize();
            if (partial.startsWith(storageRoot) && Files.isRegularFile(partial)) bytes = Files.size(partial);
        } catch (Exception ignored) { }
        jdbc.update("""
                UPDATE external_asset SET import_status='PARTIAL',download_bytes=?,download_error=? WHERE id=?
                """, bytes, message.substring(0, Math.min(1000, message.length())), assetId);
    }

    private Long responseTotal(HttpURLConnection connection, long offset) {
        String contentRange = connection.getHeaderField("Content-Range");
        if (contentRange != null && contentRange.matches("bytes \\d+-\\d+/\\d+")) {
            return Long.parseLong(contentRange.substring(contentRange.lastIndexOf('/') + 1));
        }
        long length = connection.getContentLengthLong();
        return length < 0 ? null : offset + length;
    }

    private boolean validContentRange(String value, long offset) {
        return value != null && value.matches("bytes " + offset + "-\\d+/\\d+");
    }

    private Long unsatisfiedTotal(String value) {
        return value != null && value.matches("bytes \\*/\\d+")
                ? Long.parseLong(value.substring(value.indexOf('/') + 1)) : null;
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }

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
