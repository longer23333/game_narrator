package cn.longer233.gamenarrator.importer;

import cn.longer233.gamenarrator.asset.AssetCatalogService;
import cn.longer233.gamenarrator.asset.AssetView;
import cn.longer233.gamenarrator.asset.ImportedMediaAsset;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class YtDlpMediaImporter {
    private final Path executable;
    private final Path importDirectory;
    private final Path transientDownloadDirectory;
    private final Path cookieDirectory;
    private final ObjectMapper objectMapper;
    private final MediaImportProperties properties;
    private final AssetCatalogService assetCatalogService;
    private final cn.longer233.gamenarrator.common.PhaseRetryExecutor retryExecutor;
    private final Semaphore worker = new Semaphore(1);

    public YtDlpMediaImporter(ObjectMapper objectMapper,
                              MediaImportProperties properties,
                              AssetCatalogService assetCatalogService,
                              cn.longer233.gamenarrator.common.PhaseRetryExecutor retryExecutor,
                              @Value("${game-narrator.storage-root}") String storageRoot) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.assetCatalogService = assetCatalogService;
        this.retryExecutor = retryExecutor;
        this.executable = Path.of(properties.getYtDlp()).toAbsolutePath().normalize();
        this.importDirectory = Path.of(storageRoot).toAbsolutePath().normalize().resolve("imports");
        this.transientDownloadDirectory = Path.of(storageRoot).toAbsolutePath().normalize()
                .resolve("import-downloads");
        this.cookieDirectory = Path.of(storageRoot).toAbsolutePath().normalize().resolve("import-auth");
    }

    public boolean available() {
        return Files.isRegularFile(executable);
    }

    public Path executable() {
        return executable;
    }

    public String uploadCookies(MultipartFile file, String sourceUrl) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择 cookies.txt 文件");
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("cookies.txt 不能超过 2 MB");
        }
        try {
            URI source = validateSource(sourceUrl);
            MediaImportProperties.Platform platform = requirePlatform(source.getHost());
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<String> lines = content.lines().toList();
            if (lines.isEmpty() || (!lines.getFirst().startsWith("# Netscape HTTP Cookie File")
                    && !lines.getFirst().startsWith("# HTTP Cookie File"))) {
                throw new IllegalArgumentException("Cookie 文件必须是 Netscape cookies.txt 格式");
            }
            List<String> filtered = new ArrayList<>();
            filtered.add("# Netscape HTTP Cookie File");
            for (String line : lines) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\\t", -1);
                if (fields.length < 7) continue;
                String domain = fields[0].toLowerCase(Locale.ROOT);
                if (platform.acceptsCookieDomain(domain)) {
                    filtered.add(line);
                }
            }
            if (filtered.size() == 1) {
                throw new IllegalArgumentException(
                        "Cookie 文件中没有 " + platform.getId() + " 登录信息");
            }
            Files.createDirectories(cookieDirectory);
            purgeExpiredCookies();
            String token = UUID.randomUUID().toString();
            Path output = cookieDirectory.resolve(token + ".txt");
            Files.writeString(output, String.join(System.lineSeparator(), filtered)
                    + System.lineSeparator(), StandardCharsets.UTF_8);
            return token;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("无法保存临时 Cookie 文件", exception);
        }
    }

    public ResolvedMedia resolve(MediaResolveRequest request) {
        requireAvailable();
        URI uri = validateSource(request.url());
        List<String> command = new ArrayList<>(List.of(executable.toString(), "--dump-single-json",
                "--skip-download", "--no-playlist", "--no-warnings"));
        JsonNode root = readJson(runAuthenticated(command, uri, null,
                request.cookieToken(), request.url(), Duration.ofMinutes(2)));
        List<MediaVariant> variants = new ArrayList<>();
        root.path("formats").forEach(format -> {
            String formatId = format.path("format_id").asText("");
            if (formatId.isBlank() || !formatId.matches("[A-Za-z0-9_+\\-]+")) return;
            int width = format.path("width").asInt(0);
            int height = format.path("height").asInt(0);
            String vcodec = format.path("vcodec").asText("none");
            String acodec = format.path("acodec").asText("none");
            if ("none".equals(vcodec) && "none".equals(acodec)) return;
            String note = format.path("format_note").asText("");
            String label = height > 0 ? height + "p" : ("none".equals(vcodec) ? "仅音频" : note);
            variants.add(new MediaVariant(formatId, label, format.path("ext").asText(),
                    width == 0 ? null : width, height == 0 ? null : height,
                    format.path("fps").isNumber() ? format.path("fps").asDouble() : null,
                    vcodec, acodec, approximateSize(format)));
        });
        variants.sort(Comparator.comparing((MediaVariant item) ->
                item.height() == null ? 0 : item.height()).reversed());
        if (variants.isEmpty()) {
            variants.add(new MediaVariant("best", "自动选择最佳可播放格式", "mp4",
                    null, null, null, "unknown", "unknown", null));
        }
        List<String> tags = new ArrayList<>();
        root.path("tags").forEach(tag -> tags.add(tag.asText()));
        return new ResolvedMedia(requirePlatform(uri.getHost()).getId(), root.path("id").asText(),
                root.path("title").asText("未命名视频"), root.path("uploader").asText(null),
                normalizeThumbnail(root.path("thumbnail").asText(null)),
                null,
                root.path("duration").isNumber() ? root.path("duration").asDouble() : null,
                tags.stream().filter(value -> !value.isBlank()).limit(20).toList(),
                variants.stream().limit(40).toList());
    }

    private String normalizeThumbnail(String value) {
        if (value == null || value.isBlank()) return null;
        URI uri = URI.create(value);
        return RemoteThumbnailService.upgradeToHttps(uri).toString();
    }

    public MediaDownloadResult download(MediaDownloadRequest request) {
        return download(request, progress -> { });
    }

    public MediaDownloadResult download(MediaDownloadRequest request,
                                        java.util.function.Consumer<MediaDownloadProgress> progressConsumer) {
        return retryExecutor.download(() -> downloadOnce(request, progressConsumer));
    }

    private MediaDownloadResult downloadOnce(MediaDownloadRequest request,
                                        java.util.function.Consumer<MediaDownloadProgress> progressConsumer) {
        requireAvailable();
        validateSource(request.url());
        if (!worker.tryAcquire()) throw new IllegalStateException("已有一个平台素材下载任务正在运行");
        try {
            Path targetDirectory = request.addToLibrary() ? importDirectory : transientDownloadDirectory;
            Files.createDirectories(targetDirectory);
            purgeTransientDownloads();
            String marker = "FINAL_FILE:";
            URI uri = validateSource(request.url());
            List<String> command = new ArrayList<>(List.of(executable.toString(), "--no-playlist",
                    "--no-warnings", "--newline", "--restrict-filenames",
                    "--progress-template", "download:GNPROGRESS:%(progress._percent_str)s|%(progress.downloaded_bytes)s|%(progress.total_bytes_estimate)s|%(progress._speed_str)s|%(progress._eta_str)s",
                    "--merge-output-format", "mp4", "--format", playableFormatSelector(request.formatId()),
                    "--output", targetDirectory.resolve("%(extractor)s-%(id)s-%(title).80s.%(ext)s").toString(),
                    "--print", "after_move:" + marker + "%(filepath)s"));
            if (request.subtitles()) {
                command.addAll(List.of("--write-subs", "--write-auto-subs", "--sub-langs", "zh.*,ja.*,en.*",
                        "--sub-format", "srt/best", "--convert-subs", "srt"));
            }
            String output = runAuthenticated(command, uri, null,
                    request.cookieToken(), request.url(), Duration.ofHours(2), line -> {
                        if (!line.startsWith("GNPROGRESS:")) return;
                        String[] values = line.substring(11).split("\\|", -1);
                        if (values.length >= 5) progressConsumer.accept(new MediaDownloadProgress(
                                values[0].trim(), values[1], values[2], values[3].trim(), values[4].trim()));
                    });
            String pathText = output.lines().filter(line -> line.startsWith(marker))
                    .map(line -> line.substring(marker.length())).reduce((first, second) -> second)
                    .orElseThrow(() -> new IllegalStateException("下载完成但未返回文件路径"));
            Path outputPath = Path.of(pathText).toAbsolutePath().normalize();
            if (!outputPath.startsWith(targetDirectory) || !Files.isRegularFile(outputPath)) {
                throw new IllegalStateException("下载结果不在授权的素材目录中");
            }
            long size = Files.size(outputPath);
            AssetView asset = request.addToLibrary()
                    ? assetCatalogService.registerImportedMedia(new ImportedMediaAsset(
                            request.url(), request.title(), request.creator(), request.thumbnail(),
                            request.durationSeconds(), request.tags()), outputPath)
                    : null;
            Path subtitlePath = request.subtitles() ? findSubtitle(outputPath) : null;
            return new MediaDownloadResult("COMPLETED", outputPath.toString(),
                    outputPath.getFileName().toString(), size,
                    asset == null ? null : asset.id(), null,
                    subtitlePath == null ? null : subtitlePath.toString());
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取下载结果：" + exception.getMessage(), exception);
        } finally {
            worker.release();
        }
    }

    private Path findSubtitle(Path video) throws IOException {
        String name = video.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot < 0 ? name : name.substring(0, dot);
        try (var paths = Files.list(video.getParent())) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(stem + "."))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".srt"))
                    .sorted(Comparator.comparing((Path path) -> subtitlePreference(path.getFileName().toString())))
                    .findFirst().orElse(null);
        }
    }

    private int subtitlePreference(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains(".zh")) return 0;
        if (lower.contains(".ja")) return 1;
        if (lower.contains(".en")) return 2;
        return 3;
    }

    static String playableFormatSelector(String requested) {
        String value = requested == null ? "" : requested.trim();
        if (value.isBlank() || "best".equalsIgnoreCase(value)) {
            return "bestvideo+bestaudio/best";
        }
        if (value.contains("+") || value.contains("/")) return value;
        return value + "+bestaudio/" + value + "/bestvideo+bestaudio/best";
    }

    private String run(List<String> command, Duration timeout,
                       java.util.function.Consumer<String> outputLine) {
        try {
            var result = cn.longer233.gamenarrator.common.ExternalProcessRunner.run(
                    command, timeout, null, outputLine);
            String output = result.output();
            if (result.exitCode() != 0) {
                String safe = output.length() > 1200 ? output.substring(output.length() - 1200) : output;
                if (safe.contains("HTTP Error 412") && safe.contains("BiliBili")) {
                    throw new IllegalStateException(
                            "Bilibili 拒绝了匿名请求（HTTP 412）。请选择已登录 Bilibili 的浏览器后重试。");
                }
                if (safe.contains("Failed to decrypt with DPAPI")) {
                    throw new IllegalStateException(
                            "Windows 无法解密浏览器 Cookie。请上传 Netscape 格式的 cookies.txt 后重试。");
                }
                throw new IllegalStateException("媒体工具执行失败：" + safe.trim());
            }
            return output;
        } catch (cn.longer233.gamenarrator.common.ExternalProcessRunner.ProcessTimeoutException exception) {
            throw new IllegalStateException("媒体解析或下载超时", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("无法启动媒体导入工具：" + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("媒体导入已中断", exception);
        }
    }

    private String runAuthenticated(List<String> baseCommand, URI source, String requestedBrowser,
                                    String cookieToken, String url, Duration timeout) {
        return runAuthenticated(baseCommand, source, requestedBrowser, cookieToken, url, timeout, line -> { });
    }

    private String runAuthenticated(List<String> baseCommand, URI source, String requestedBrowser,
                                    String cookieToken, String url, Duration timeout,
                                    java.util.function.Consumer<String> outputLine) {
        if (cookieToken != null && !cookieToken.isBlank()) {
            return runAttempt(baseCommand, source, null, cookieToken, url, timeout, outputLine);
        }
        if (requestedBrowser != null && !requestedBrowser.isBlank()) {
            return runAttempt(baseCommand, source, requestedBrowser, null, url, timeout, outputLine);
        }
        List<String> attempts = new ArrayList<>();
        try {
            return runAttempt(baseCommand, source, null, null, url, timeout, outputLine);
        } catch (IllegalStateException exception) {
            if (!isAuthenticationFailure(exception)) throw exception;
            attempts.add("anonymous");
        }
        if (!properties.isLocalAuthenticationDiscovery()) {
            throw new IllegalStateException(
                    "平台要求登录认证。请上传从本人浏览器导出的 Netscape cookies.txt；"
                            + "服务器不会读取本机浏览器、下载目录或桌面文件。");
        }
        CookieDiscovery discovery = discoverCookieToken(source);
        if (discovery.token() != null) {
            attempts.add("downloaded-cookie-file");
            try {
                return runAttempt(baseCommand, source, null, discovery.token(), url, timeout, outputLine);
            } catch (IllegalStateException exception) {
                if (!isAuthenticationFailure(exception)) throw exception;
            }
        }
        for (String browser : properties.getBrowserPriority()) {
            try {
                return runAttempt(baseCommand, source, browser, null, url, timeout, outputLine);
            } catch (IllegalStateException exception) {
                if (!isAuthenticationFailure(exception)) throw exception;
                attempts.add(browser);
            }
        }
        throw new IllegalStateException("自动认证失败（已尝试 " + String.join("、", attempts)
                + "）。" + discovery.message());
    }

    private CookieDiscovery discoverCookieToken(URI source) {
        if (!properties.isLocalAuthenticationDiscovery()) {
            return new CookieDiscovery(null, "服务器本地认证发现已关闭。");
        }
        MediaImportProperties.Platform platform = requirePlatform(source.getHost());
        java.time.Instant cutoff = java.time.Instant.now().minus(Duration.ofDays(7));
        List<Path> candidates = new ArrayList<>();
        for (Path directory : cookieSearchDirectories()) {
            if (!Files.isDirectory(directory)) continue;
            try (var paths = Files.list(directory)) {
                candidates.addAll(paths.filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                            return name.endsWith(".txt");
                        })
                        .filter(path -> isRecentSmallFile(path, cutoff))
                        .toList());
            } catch (IOException ignored) {
                // Continue with other configured directories.
            }
        }
        candidates.sort(Comparator.comparing(this::lastModified).reversed());
        for (Path candidate : candidates) {
            try {
                String token = storeFilteredCookies(
                        Files.readString(candidate, StandardCharsets.UTF_8), platform);
                if (token != null) return new CookieDiscovery(token, "");
            } catch (Exception ignored) {
                // Ignore invalid candidates without logging sensitive local paths.
            }
        }
        if (candidates.isEmpty()) {
            return new CookieDiscovery(null,
                    "未在系统下载目录或桌面发现近 7 天的 cookies.txt。请先从已登录浏览器导出 Netscape 格式 Cookie，程序会自动识别，无需手动上传。");
        }
        return new CookieDiscovery(null,
                "已发现 " + candidates.size() + " 个文本文件，但其中没有当前平台可用的 Netscape Cookie。");
    }

    private List<Path> cookieSearchDirectories() {
        if (!properties.isLocalAuthenticationDiscovery()) return List.of();
        java.util.LinkedHashSet<Path> directories = new java.util.LinkedHashSet<>();
        for (String configuredPath : properties.getCookieSearchPaths()) {
            if (configuredPath == null || configuredPath.isBlank()) continue;
            directories.add(Path.of(configuredPath).toAbsolutePath().normalize());
        }
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            Path home = Path.of(userHome).toAbsolutePath().normalize();
            directories.add(home.resolve("Downloads"));
            directories.add(home.resolve("Desktop"));
            directories.add(home.resolve("OneDrive").resolve("Downloads"));
            directories.add(home.resolve("OneDrive").resolve("Desktop"));
        }
        return List.copyOf(directories);
    }

    private record CookieDiscovery(String token, String message) {}

    private boolean isRecentSmallFile(Path path, java.time.Instant cutoff) {
        try {
            return Files.size(path) <= 2 * 1024 * 1024
                    && Files.getLastModifiedTime(path).toInstant().isAfter(cutoff);
        } catch (IOException exception) {
            return false;
        }
    }

    private String storeFilteredCookies(String content, MediaImportProperties.Platform platform)
            throws IOException {
        List<String> lines = content.lines().toList();
        if (lines.isEmpty() || (!lines.getFirst().startsWith("# Netscape HTTP Cookie File")
                && !lines.getFirst().startsWith("# HTTP Cookie File"))) return null;
        List<String> filtered = new ArrayList<>();
        filtered.add("# Netscape HTTP Cookie File");
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] fields = line.split("\\t", -1);
            if (fields.length >= 7 && platform.acceptsCookieDomain(fields[0])) filtered.add(line);
        }
        if (filtered.size() == 1) return null;
        Files.createDirectories(cookieDirectory);
        purgeExpiredCookies();
        String token = UUID.randomUUID().toString();
        Files.writeString(cookieDirectory.resolve(token + ".txt"),
                String.join(System.lineSeparator(), filtered) + System.lineSeparator(),
                StandardCharsets.UTF_8);
        return token;
    }

    private java.time.Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException exception) {
            return java.time.Instant.EPOCH;
        }
    }

    private String runAttempt(List<String> baseCommand, URI source, String browser,
                              String cookieToken, String url, Duration timeout) {
        return runAttempt(baseCommand, source, browser, cookieToken, url, timeout, line -> { });
    }

    private String runAttempt(List<String> baseCommand, URI source, String browser,
                              String cookieToken, String url, Duration timeout,
                              java.util.function.Consumer<String> outputLine) {
        try {
            return run(commandWithAuthentication(
                    baseCommand, source, browser, cookieToken, url, true), timeout, outputLine);
        } catch (IllegalStateException exception) {
            if (!isImpersonationFallbackFailure(exception)) throw exception;
            return run(commandWithAuthentication(
                    baseCommand, source, browser, cookieToken, url, false), timeout, outputLine);
        }
    }

    boolean isImpersonationFallbackFailure(IllegalStateException exception) {
        String message = String.valueOf(exception.getMessage()).toLowerCase(Locale.ROOT);
        return message.contains("curl: (35)")
                || message.contains("sslerror")
                || message.contains("connection was reset")
                || message.contains("recv failure")
                || message.contains("no video formats found")
                || message.contains("impersonate target") && message.contains("not available");
    }

    private List<String> commandWithAuthentication(List<String> baseCommand, URI source,
                                                   String browser, String cookieToken, String url,
                                                   boolean impersonate) {
        List<String> command = new ArrayList<>(baseCommand);
        addRequestOptions(command, source, browser, cookieToken, impersonate);
        command.add(url);
        return command;
    }

    private boolean isAuthenticationFailure(IllegalStateException exception) {
        String message = String.valueOf(exception.getMessage()).toLowerCase(Locale.ROOT);
        return message.contains("412") || message.contains("401") || message.contains("403")
                || message.contains("cookie") || message.contains("dpapi")
                || message.contains("sign in") || message.contains("login")
                || message.contains("no video formats found");
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException("媒体工具返回了无法识别的数据", exception);
        }
    }

    private URI validateSource(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
            throw new IllegalArgumentException("只允许 HTTPS 平台链接");
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (properties.getPlatforms().stream().noneMatch(platform ->
                platform.matchesHost(host)))
            throw new IllegalArgumentException("当前只支持 Bilibili、YouTube、抖音和 TikTok 链接");
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isSiteLocalAddress() || address.isLinkLocalAddress())
                    throw new IllegalArgumentException("禁止访问本地或内网地址");
            }
        } catch (java.net.UnknownHostException exception) {
            throw new IllegalArgumentException("平台地址无法解析");
        }
        return uri;
    }

    private void addRequestOptions(List<String> command, URI source,
                                   String cookieBrowser, String cookieToken,
                                   boolean impersonate) {
        if (properties.isForceIpv4()) command.add("--force-ipv4");
        if (impersonate) command.addAll(List.of("--impersonate", ""));
        MediaImportProperties.Platform platform = requirePlatform(source.getHost());
        if (platform.getReferer() != null && !platform.getReferer().isBlank()) {
            command.addAll(List.of("--referer", platform.getReferer()));
        }
        Path cookieFile = resolveCookieFile(cookieToken);
        if (cookieFile != null) {
            command.addAll(List.of("--cookies", cookieFile.toString()));
        } else if (cookieBrowser != null && !cookieBrowser.isBlank()) {
            command.addAll(List.of("--cookies-from-browser", cookieBrowser));
        }
    }

    private Path resolveCookieFile(String token) {
        if (token == null || token.isBlank()) return null;
        UUID parsed;
        try {
            parsed = UUID.fromString(token);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Cookie token is invalid");
        }
        Path path = cookieDirectory.resolve(parsed + ".txt").toAbsolutePath().normalize();
        if (!path.startsWith(cookieDirectory) || !Files.isRegularFile(path)) {
            throw new IllegalStateException("临时 Cookie 已失效，请重新上传");
        }
        try {
            if (Files.getLastModifiedTime(path).toInstant()
                    .isBefore(java.time.Instant.now().minus(Duration.ofHours(1)))) {
                Files.deleteIfExists(path);
                throw new IllegalStateException("临时 Cookie 已过期，请重新上传");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取临时 Cookie", exception);
        }
        return path;
    }

    public void discardCookies(String token) {
        if (token == null || token.isBlank()) return;
        try {
            UUID parsed = UUID.fromString(token);
            Path path = cookieDirectory.resolve(parsed + ".txt").toAbsolutePath().normalize();
            if (path.startsWith(cookieDirectory)) Files.deleteIfExists(path);
        } catch (IllegalArgumentException | IOException ignored) {
            // Invalid or already removed client credentials require no further action.
        }
    }

    private void purgeExpiredCookies() throws IOException {
        if (!Files.isDirectory(cookieDirectory)) return;
        java.time.Instant cutoff = java.time.Instant.now().minus(Duration.ofHours(1));
        try (var paths = Files.list(cookieDirectory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private void purgeTransientDownloads() throws IOException {
        if (!Files.isDirectory(transientDownloadDirectory)) return;
        java.time.Instant cutoff = java.time.Instant.now().minus(Duration.ofHours(1));
        try (var paths = Files.list(transientDownloadDirectory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private Long approximateSize(JsonNode format) {
        if (format.path("filesize").isNumber()) return format.path("filesize").asLong();
        if (format.path("filesize_approx").isNumber()) return format.path("filesize_approx").asLong();
        return null;
    }

    private MediaImportProperties.Platform requirePlatform(String host) {
        return properties.getPlatforms().stream()
                .filter(platform -> platform.matchesHost(host))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("当前平台未配置"));
    }

    private void requireAvailable() {
        if (!available()) throw new IllegalStateException(
                "媒体导入工具未安装，请运行 .\\scripts\\setup-media-importer.ps1");
    }
}
