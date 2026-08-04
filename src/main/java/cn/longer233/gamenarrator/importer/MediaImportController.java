package cn.longer233.gamenarrator.importer;

import cn.longer233.gamenarrator.asset.AssetCatalogService;
import cn.longer233.gamenarrator.asset.AssetReferenceRequest;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/media-import")
public class MediaImportController {
    private static final String COOKIE_TOKENS = MediaImportController.class.getName() + ".COOKIE_TOKENS";
    private static final String DOWNLOAD_GRANTS = MediaImportController.class.getName() + ".DOWNLOAD_GRANTS";
    private static final String THUMBNAIL_GRANTS = MediaImportController.class.getName() + ".THUMBNAIL_GRANTS";
    private static final String PREVIEW_GRANTS = MediaImportController.class.getName() + ".PREVIEW_GRANTS";
    private static final String JOB_DOWNLOAD_URLS = MediaImportController.class.getName() + ".JOB_DOWNLOAD_URLS";
    private static final String JOB_PREVIEW_URLS = MediaImportController.class.getName() + ".JOB_PREVIEW_URLS";
    private final YtDlpMediaImporter importer;
    private final RemoteThumbnailService thumbnailService;
    private final MediaDownloadJobService downloadJobs;
    private final PlatformContentClassifier contentClassifier;
    private final AssetCatalogService assetCatalogService;
    private final RemoteProjectImportService projectImports;

    public MediaImportController(YtDlpMediaImporter importer, RemoteThumbnailService thumbnailService,
                                 MediaDownloadJobService downloadJobs, PlatformContentClassifier contentClassifier,
                                 AssetCatalogService assetCatalogService,
                                 RemoteProjectImportService projectImports) {
        this.importer = importer;
        this.thumbnailService = thumbnailService;
        this.downloadJobs = downloadJobs;
        this.contentClassifier = contentClassifier;
        this.assetCatalogService = assetCatalogService;
        this.projectImports = projectImports;
    }

    @PostMapping("/projects")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public cn.longer233.gamenarrator.task.application.VideoTaskView createProject(
            @Valid @RequestBody RemoteProjectImportRequest request, HttpSession session) {
        requireOwnedToken(session, request.media().cookieToken());
        return projectImports.start(request);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("available", importer.available(), "authentication", "CLIENT_COOKIE_UPLOAD");
    }

    @GetMapping("/browser-auth/config")
    public Map<String, Object> browserAuthenticationConfig(@RequestParam("url") String url) {
        return importer.browserAuthenticationConfig(url);
    }

    @PostMapping("/resolve")
    public ResolvedMedia resolve(@Valid @RequestBody MediaResolveRequest request, HttpSession session) {
        requireOwnedToken(session, request.cookieToken());
        ResolvedMedia media = importer.resolve(request);
        String previewUrl = null;
        if (media.thumbnail() != null && !media.thumbnail().isBlank()) {
            String token = UUID.randomUUID().toString();
            Map<String, ThumbnailGrant> grants = thumbnailGrants(session);
            if (grants.size() >= 20) grants.remove(grants.keySet().iterator().next());
            grants.put(token, new ThumbnailGrant(media.thumbnail(), request.url()));
            previewUrl = "/api/media-import/thumbnails/" + token;
        }
        ContentOriginAssessment assessment = contentClassifier.assess(media);
        java.util.List<String> candidateTags = new java.util.ArrayList<>(media.tags());
        candidateTags.add("来源判断:" + assessment.label());
        assetCatalogService.registerReference(new AssetReferenceRequest(media.platform(), request.url(), media.thumbnail(), null,
                limit(media.title(), 200), limit(media.creator(), 120), "VIDEO", "RIGHTS_REVIEW_REQUIRED", null,
                "公开可见不等于获得下载或再创作授权", candidateTags.stream().distinct().limit(20).toList()));
        return new ResolvedMedia(media.platform(), media.sourceId(), media.title(), media.creator(),
                media.thumbnail(), previewUrl, media.durationSeconds(), media.tags(), media.variants(),
                assessment.code(), assessment.label(), assessment.confidence(), assessment.reason());
    }

    private String limit(String value, int maximum) {
        if (value == null) return null;
        return value.substring(0, Math.min(maximum, value.length()));
    }

    @PostMapping(value = "/cookies", consumes = "multipart/form-data")
    public Map<String, String> uploadCookies(@RequestParam("file") MultipartFile file,
                                             @RequestParam("url") String url,
                                             HttpSession session) {
        String token = importer.uploadCookies(file, url);
        ownedTokens(session).add(token);
        return Map.of("token", token);
    }

    @PostMapping("/download")
    public MediaDownloadResult download(@Valid @RequestBody MediaDownloadRequest request, HttpSession session) {
        requireOwnedToken(session, request.cookieToken());
        MediaDownloadResult result = importer.download(request);
        if (request.cookieToken() != null && !request.cookieToken().isBlank()) {
            ownedTokens(session).remove(request.cookieToken());
            importer.discardCookies(request.cookieToken());
        }
        String downloadToken = UUID.randomUUID().toString();
        downloadGrants(session).put(downloadToken,
                new DownloadGrant(result.localPath(), result.fileName(), !request.addToLibrary()));
        return new MediaDownloadResult(result.status(), result.localPath(), result.fileName(),
                result.sizeBytes(), result.assetId(), "/api/media-import/files/" + downloadToken);
    }

    @PostMapping("/download-jobs")
    public Map<String, String> startDownload(@Valid @RequestBody MediaDownloadRequest request,
                                             HttpSession session) {
        requireOwnedToken(session, request.cookieToken());
        return Map.of("id", downloadJobs.start(request, session.getId()));
    }

    @GetMapping("/download-jobs/{id}")
    public ResponseEntity<MediaDownloadJobView> downloadStatus(@PathVariable UUID id,
                                                               HttpSession session) {
        MediaDownloadJobView job = downloadJobs.find(id.toString(), session.getId());
        if (job == null) return ResponseEntity.notFound().build();
        if (!"COMPLETED".equals(job.status())) return ResponseEntity.ok(job);
        String url = jobDownloadUrls(session).get(id.toString());
        if (url == null) {
            String token = UUID.randomUUID().toString();
            MediaDownloadResult result = job.result();
            downloadGrants(session).put(token,
                    new DownloadGrant(result.localPath(), result.fileName(), result.assetId() == null));
            url = "/api/media-import/files/" + token;
            jobDownloadUrls(session).put(id.toString(), url);
        }
        MediaDownloadResult result = job.result();
        MediaDownloadResult publicResult = new MediaDownloadResult(result.status(), result.localPath(),
                result.fileName(), result.sizeBytes(), result.assetId(), url, result.platformSubtitlePath());
        return ResponseEntity.ok(new MediaDownloadJobView(job.id(), job.status(), job.progress(),
                publicResult, job.error()));
    }

    @PostMapping("/preview-jobs")
    public Map<String, String> startPreview(@Valid @RequestBody MediaDownloadRequest request,
                                            HttpSession session) {
        requireOwnedToken(session, request.cookieToken());
        String lightweightFormat = "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]"
                + "/best[height<=480][ext=mp4]/best[height<=480]";
        MediaDownloadRequest previewRequest = new MediaDownloadRequest(request.url(), lightweightFormat,
                false, false, request.rightsConfirmed(), request.cookieToken(), request.title(),
                request.creator(), request.thumbnail(), request.durationSeconds(), request.tags());
        return Map.of("id", downloadJobs.start(previewRequest, session.getId()));
    }

    @GetMapping("/preview-jobs/{id}")
    public ResponseEntity<MediaDownloadJobView> previewStatus(@PathVariable UUID id,
                                                              HttpSession session) {
        MediaDownloadJobView job = downloadJobs.find(id.toString(), session.getId());
        if (job == null) return ResponseEntity.notFound().build();
        if (!"COMPLETED".equals(job.status())) return ResponseEntity.ok(job);
        String url = jobPreviewUrls(session).get(id.toString());
        if (url == null) {
            String token = UUID.randomUUID().toString();
            MediaDownloadResult result = job.result();
            Map<String, PreviewGrant> grants = previewGrants(session);
            if (grants.size() >= 5) grants.remove(grants.keySet().iterator().next());
            grants.put(token, new PreviewGrant(result.localPath(), result.fileName()));
            url = "/api/media-import/previews/" + token;
            jobPreviewUrls(session).put(id.toString(), url);
        }
        MediaDownloadResult result = job.result();
        MediaDownloadResult publicResult = new MediaDownloadResult(result.status(), result.localPath(),
                result.fileName(), result.sizeBytes(), null, url);
        return ResponseEntity.ok(new MediaDownloadJobView(job.id(), job.status(), job.progress(),
                publicResult, job.error()));
    }

    @PostMapping("/preview")
    public MediaPreviewResult createPreview(@Valid @RequestBody MediaDownloadRequest request,
                                            HttpSession session) {
        requireOwnedToken(session, request.cookieToken());
        MediaDownloadRequest previewRequest = new MediaDownloadRequest(request.url(), request.formatId(),
                false, false, request.rightsConfirmed(), request.cookieToken(), request.title(),
                request.creator(), request.thumbnail(), request.durationSeconds(), request.tags());
        MediaDownloadResult result = importer.download(previewRequest);
        String token = UUID.randomUUID().toString();
        Map<String, PreviewGrant> grants = previewGrants(session);
        if (grants.size() >= 5) grants.remove(grants.keySet().iterator().next());
        grants.put(token, new PreviewGrant(result.localPath(), result.fileName()));
        return new MediaPreviewResult("/api/media-import/previews/" + token,
                result.fileName(), result.sizeBytes());
    }

    @GetMapping("/previews/{token}")
    public ResponseEntity<FileSystemResource> preview(@PathVariable UUID token, HttpSession session) {
        PreviewGrant grant = previewGrants(session).get(token.toString());
        if (grant == null) return ResponseEntity.notFound().build();
        Path path = Path.of(grant.path()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("video/mp4"))
                .contentLength(fileSize(path))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(grant.fileName(), StandardCharsets.UTF_8)
                                .build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(new FileSystemResource(path));
    }

    @GetMapping("/files/{token}")
    public ResponseEntity<StreamingResponseBody> downloadFile(@PathVariable UUID token, HttpSession session) {
        DownloadGrant grant = downloadGrants(session).remove(token.toString());
        if (grant == null) throw new IllegalStateException("下载地址已失效或不属于当前浏览器会话");
        Path path = Path.of(grant.path()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IllegalStateException("下载文件已不存在");
        StreamingResponseBody body = output -> {
            try (var input = Files.newInputStream(path)) {
                input.transferTo(output);
            } finally {
                if (grant.deleteAfterDownload()) Files.deleteIfExists(path);
            }
        };
        String disposition = ContentDisposition.attachment()
                .filename(grant.fileName(), StandardCharsets.UTF_8).build().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CONTENT_LENGTH, Long.toString(fileSize(path)))
                .body(body);
    }

    @GetMapping("/thumbnails/{token}")
    public ResponseEntity<byte[]> thumbnail(@PathVariable UUID token, HttpSession session) {
        ThumbnailGrant grant = thumbnailGrants(session).get(token.toString());
        if (grant == null) return ResponseEntity.notFound().build();
        RemoteThumbnailService.ThumbnailContent content =
                thumbnailService.fetch(grant.thumbnailUrl(), grant.sourceUrl());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, content.contentType())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=900")
                .body(content.bytes());
    }

    @SuppressWarnings("unchecked")
    private Set<String> ownedTokens(HttpSession session) {
        Object existing = session.getAttribute(COOKIE_TOKENS);
        if (existing instanceof Set<?> set) return (Set<String>) set;
        Set<String> created = new HashSet<>();
        session.setAttribute(COOKIE_TOKENS, created);
        return created;
    }

    private void requireOwnedToken(HttpSession session, String token) {
        if (token != null && !token.isBlank() && !ownedTokens(session).contains(token)) {
            throw new IllegalStateException("临时 Cookie 不属于当前浏览器会话，请重新上传");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, DownloadGrant> downloadGrants(HttpSession session) {
        Object existing = session.getAttribute(DOWNLOAD_GRANTS);
        if (existing instanceof Map<?, ?> map) return (Map<String, DownloadGrant>) map;
        Map<String, DownloadGrant> created = new HashMap<>();
        session.setAttribute(DOWNLOAD_GRANTS, created);
        return created;
    }

    @SuppressWarnings("unchecked")
    private Map<String, ThumbnailGrant> thumbnailGrants(HttpSession session) {
        Object existing = session.getAttribute(THUMBNAIL_GRANTS);
        if (existing instanceof Map<?, ?> map) return (Map<String, ThumbnailGrant>) map;
        Map<String, ThumbnailGrant> created = new java.util.LinkedHashMap<>();
        session.setAttribute(THUMBNAIL_GRANTS, created);
        return created;
    }

    @SuppressWarnings("unchecked")
    private Map<String, PreviewGrant> previewGrants(HttpSession session) {
        Object existing = session.getAttribute(PREVIEW_GRANTS);
        if (existing instanceof Map<?, ?> map) return (Map<String, PreviewGrant>) map;
        Map<String, PreviewGrant> created = new java.util.LinkedHashMap<>();
        session.setAttribute(PREVIEW_GRANTS, created);
        return created;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> jobDownloadUrls(HttpSession session) {
        Object existing = session.getAttribute(JOB_DOWNLOAD_URLS);
        if (existing instanceof Map<?, ?> map) return (Map<String, String>) map;
        Map<String, String> created = new HashMap<>();
        session.setAttribute(JOB_DOWNLOAD_URLS, created);
        return created;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> jobPreviewUrls(HttpSession session) {
        Object existing = session.getAttribute(JOB_PREVIEW_URLS);
        if (existing instanceof Map<?, ?> map) return (Map<String, String>) map;
        Map<String, String> created = new HashMap<>();
        session.setAttribute(JOB_PREVIEW_URLS, created);
        return created;
    }

    private long fileSize(Path path) {
        try { return Files.size(path); }
        catch (java.io.IOException exception) { throw new IllegalStateException("无法读取下载文件", exception); }
    }

    private record DownloadGrant(String path, String fileName, boolean deleteAfterDownload)
            implements java.io.Serializable { }
    private record ThumbnailGrant(String thumbnailUrl, String sourceUrl)
            implements java.io.Serializable { }
    private record PreviewGrant(String path, String fileName)
            implements java.io.Serializable { }
}
