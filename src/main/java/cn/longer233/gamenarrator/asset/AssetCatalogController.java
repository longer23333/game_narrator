package cn.longer233.gamenarrator.asset;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.UUID;
import java.nio.file.Path;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import cn.longer233.gamenarrator.importer.RemoteThumbnailService;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/assets")
public class AssetCatalogController {
    private static final byte[] THUMBNAIL_PLACEHOLDER = ("""
            <svg xmlns="http://www.w3.org/2000/svg" width="640" height="360" viewBox="0 0 640 360">
              <rect width="640" height="360" fill="#10182b"/>
              <path d="M246 112h148a18 18 0 0 1 18 18v100a18 18 0 0 1-18 18H246a18 18 0 0 1-18-18V130a18 18 0 0 1 18-18Z" fill="#202e49" stroke="#61718f" stroke-width="4"/>
              <path d="m250 224 43-48 31 31 24-25 42 42Z" fill="#61718f"/>
              <circle cx="367" cy="151" r="15" fill="#91a1bd"/>
              <text x="320" y="292" text-anchor="middle" fill="#91a1bd" font-family="sans-serif" font-size="18">远程封面暂不可用</text>
            </svg>
            """).getBytes(StandardCharsets.UTF_8);
    private final AssetCatalogService service;
    private final AssetLibraryProperties properties;
    private final SafeRemoteHttpConnector remoteConnector;
    private final RemoteThumbnailService thumbnailService;

    public AssetCatalogController(AssetCatalogService service, AssetLibraryProperties properties,
                                  SafeRemoteHttpConnector remoteConnector,
                                  RemoteThumbnailService thumbnailService) {
        this.service = service;
        this.properties = properties;
        this.remoteConnector = remoteConnector;
        this.thumbnailService = thumbnailService;
    }

    @PostMapping({"/discover", "/discover/openverse"})
    public List<AssetView> discover(@Valid @RequestBody AssetSearchRequest request) {
        return service.discover(request);
    }

    @GetMapping("/discover/featured")
    public List<AssetView> discoverFeatured() {
        return service.discoverFeatured();
    }

    @GetMapping("/sources")
    public List<DomesticSourceView> sources() {
        return properties.getDomesticSources().stream().filter(AssetLibraryProperties.DomesticSource::isEnabled)
                .sorted(java.util.Comparator.comparingInt(AssetLibraryProperties.DomesticSource::getPriority)
                        .thenComparing(AssetLibraryProperties.DomesticSource::getName))
                .map(source -> new DomesticSourceView(source.getId(), source.getName(), source.getUrl(), source.getSearchUrl(),
                        source.getAssetTypes(), source.getRegion(), source.getPriority(), "RIGHTS_REVIEW_REQUIRED"))
                .toList();
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<FileSystemResource> preview(@PathVariable UUID id) throws java.io.IOException {
        Path path = service.previewFile(id);
        FileSystemResource resource = new FileSystemResource(path);
        MediaType mediaType = MediaTypeFactory.getMediaType(path.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(resource.contentLength())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeFileName(path) + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(resource);
    }

    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<byte[]> thumbnail(@PathVariable UUID id) {
        AssetCatalogService.RemoteThumbnailSource source = service.remoteThumbnail(id);
        try {
            return thumbnailResponse(thumbnailService.fetch(source.url(), source.referer()));
        } catch (RuntimeException primaryFailure) {
            if (source.fallbackUrl() != null && !source.fallbackUrl().equals(source.url())) {
                try {
                    return thumbnailResponse(thumbnailService.fetch(source.fallbackUrl(), source.referer()));
                } catch (RuntimeException ignored) {
                    // The local placeholder below keeps the asset grid usable while both remote sources are unavailable.
                }
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("image/svg+xml"))
                    .contentLength(THUMBNAIL_PLACEHOLDER.length)
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                    .header("X-GameNarrator-Thumbnail-Fallback", "true")
                    .body(THUMBNAIL_PLACEHOLDER);
        }
    }

    private ResponseEntity<byte[]> thumbnailResponse(RemoteThumbnailService.ThumbnailContent content) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.bytes().length)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(content.bytes());
    }

    @GetMapping("/{id}/remote-preview")
    public ResponseEntity<StreamingResponseBody> remotePreview(@PathVariable UUID id,
                                                               HttpServletRequest request) throws java.io.IOException {
        AssetCatalogService.RemotePreviewSource source = service.remoteAudioPreview(id);
        String range = request.getHeader(HttpHeaders.RANGE);
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("User-Agent", "GameNarrator/0.1");
        headers.put("Accept", "audio/*,application/octet-stream;q=0.8");
        if (range != null && range.matches("bytes=\\d*-\\d*")) headers.put("Range", range);
        HttpURLConnection connection = remoteConnector.open(source.uri(), headers, 3);
        int status = connection.getResponseCode();
        if (status != HttpStatus.OK.value() && status != HttpStatus.PARTIAL_CONTENT.value()) {
            connection.disconnect();
            throw new IllegalStateException("在线试听源返回 HTTP " + status);
        }
        long length = connection.getContentLengthLong();
        long maximum = 200L * 1024 * 1024;
        if (length > maximum) {
            connection.disconnect();
            throw new IllegalStateException("在线试听文件超过 200MB 限制");
        }
        String contentType = connection.getContentType();
        MediaType mediaType;
        try { mediaType = contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType); }
        catch (Exception ignored) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        boolean playableAudio = "audio".equalsIgnoreCase(mediaType.getType())
                || MediaType.APPLICATION_OCTET_STREAM.isCompatibleWith(mediaType)
                || "ogg".equalsIgnoreCase(mediaType.getSubtype());
        if (!playableAudio) {
            connection.disconnect();
            throw new IllegalStateException("试听地址没有返回可播放音频，而是 " + mediaType);
        }
        StreamingResponseBody body = output -> {
            try (var input = connection.getInputStream()) {
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    total += count;
                    if (total > maximum) throw new java.io.IOException("试听流超过大小限制");
                    output.write(buffer, 0, count);
                    output.flush();
                }
            } finally {
                connection.disconnect();
            }
        };
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status).contentType(mediaType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline");
        String contentRange = connection.getHeaderField(HttpHeaders.CONTENT_RANGE);
        if (contentRange != null) response.header(HttpHeaders.CONTENT_RANGE, contentRange);
        if (length >= 0) response.contentLength(length);
        return response.body(body);
    }

    private String safeFileName(Path path) {
        return path.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    @GetMapping
    public List<AssetView> list(@RequestParam(required = false) String assetType,
                                @RequestParam(required = false) String query,
                                @RequestParam(required = false) String provider,
                                @RequestParam(required = false) String importStatus,
                                @RequestParam(required = false) Boolean favorite,
                                @RequestParam(defaultValue = "false") boolean archived,
                                @RequestParam(defaultValue = "newest") String sort,
                                @RequestParam(defaultValue = "false") boolean semantic,
                                @RequestParam(defaultValue = "24") int limit) {
        return service.list(assetType, query, provider, importStatus, favorite, archived, sort, semantic, limit);
    }

    @GetMapping("/{id}/similar")
    public List<AssetView> similar(@PathVariable UUID id) {
        return service.similar(id);
    }

    @PostMapping("/repair/bilibili-metadata")
    public java.util.Map<String, Integer> repairBilibiliMetadata() {
        return java.util.Map.of("repaired", service.repairBilibiliMetadata());
    }

    @PostMapping("/references")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetView registerReference(@Valid @RequestBody AssetReferenceRequest request) {
        return service.registerReference(request);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AssetView upload(@RequestParam("file") MultipartFile file) {
        return service.uploadLocal(file);
    }

    @PostMapping("/projects/{taskId}")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetView registerCompletedProject(@PathVariable UUID taskId) {
        return service.registerCompletedProject(taskId);
    }

    @PutMapping("/{id}/tags")
    public AssetView updateTags(@PathVariable UUID id,
                                @RequestBody AssetTagUpdateRequest request) {
        return service.updateTags(id, request);
    }

    @PostMapping("/{id}/download")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AssetView download(@PathVariable UUID id) {
        return service.download(id);
    }

    @GetMapping("/{id}/download-status")
    public AssetDownloadStatus downloadStatus(@PathVariable UUID id) {
        return service.downloadStatus(id);
    }

    @PostMapping("/{id}/derive")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetView derive(@PathVariable UUID id, @Valid @RequestBody AssetDerivativeRequest request) {
        return service.derive(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PatchMapping("/{id}/state")
    public AssetView updateState(@PathVariable UUID id,
                                 @RequestBody AssetStateUpdateRequest request) {
        return service.updateState(id, request);
    }

    public record DomesticSourceView(String id, String name, String url, String searchUrl, String assetTypes,
                                     String region, int priority, String rightsPolicy) { }
}
