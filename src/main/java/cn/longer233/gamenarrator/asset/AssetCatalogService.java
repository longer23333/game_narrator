package cn.longer233.gamenarrator.asset;

import cn.longer233.gamenarrator.identity.CurrentUserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.StandardCopyOption;

@Service
public class AssetCatalogService {
    private static final Logger log = LoggerFactory.getLogger(AssetCatalogService.class);
    private static final long MAX_DOWNLOAD_BYTES = 100L * 1024 * 1024;
    private static final long MAX_UPLOAD_BYTES = 500L * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final OpenverseAssetClient openverse;
    private final WikimediaAssetClient wikimedia;
    private final BilibiliAssetClient bilibili;
    private final PexelsAssetClient pexels;
    private final PixabayAssetClient pixabay;
    private final AiAssetTagger aiTagger;
    private final ChineseAssetQueryExpander queryExpander;
    private final AssetLibraryProperties assetLibraryProperties;
    private final BgeAssetSemanticSearch semanticSearch;
    private final Executor taskExecutor;
    private final SafeRemoteHttpConnector remoteConnector;
    private final CurrentUserContext currentUser;
    private final Path storageRoot;
    private final String ffmpegCommand;
    private final Set<UUID> localizationQueued = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public AssetCatalogService(JdbcTemplate jdbc, ObjectMapper objectMapper,
                               OpenverseAssetClient openverse, WikimediaAssetClient wikimedia,
                               BilibiliAssetClient bilibili, PexelsAssetClient pexels,
                               PixabayAssetClient pixabay, AiAssetTagger aiTagger,
                               ChineseAssetQueryExpander queryExpander, AssetLibraryProperties assetLibraryProperties,
                               BgeAssetSemanticSearch semanticSearch,
                               @Qualifier("taskExecutor") Executor taskExecutor,
                               SafeRemoteHttpConnector remoteConnector,
                               CurrentUserContext currentUser,
                               @Value("${game-narrator.storage-root}") String storageRoot,
                               @Value("${game-narrator.ffmpeg-command}") String ffmpegCommand) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.openverse = openverse;
        this.wikimedia = wikimedia;
        this.bilibili = bilibili;
        this.pexels = pexels;
        this.pixabay = pixabay;
        this.aiTagger = aiTagger;
        this.queryExpander = queryExpander;
        this.assetLibraryProperties = assetLibraryProperties;
        this.semanticSearch = semanticSearch;
        this.taskExecutor = taskExecutor;
        this.remoteConnector = remoteConnector;
        this.currentUser = currentUser;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.ffmpegCommand = ffmpegCommand;
    }

    public List<AssetView> discoverFeatured() {
        LinkedHashMap<UUID, AssetView> featured = new LinkedHashMap<>();
        for (AssetLibraryProperties.FeaturedSearch search : assetLibraryProperties.getFeaturedSearches()) {
            if (search.getAssetType() == null || search.getAssetType().isBlank()
                    || search.getQuery() == null || search.getQuery().isBlank()) continue;
            AssetSearchRequest request = new AssetSearchRequest(search.getQuery(), search.getAssetType(),
                    assetLibraryProperties.getFeaturedPageSize(), 1, true, true, null, "RELEVANCE");
            try {
                for (AssetView asset : discover(request)) featured.putIfAbsent(asset.id(), asset);
            } catch (Exception exception) {
                log.warn("Featured asset search failed for type {}: {}", search.getAssetType(), exception.getMessage());
                throw new IllegalStateException("在线开放素材源不可用；当前只能展示已保存到本地的素材", exception);
            }
        }
        if (featured.isEmpty()) throw new IllegalStateException("在线开放素材源没有返回可用内容");
        return new ArrayList<>(featured.values());
    }

    public List<AssetView> discover(AssetSearchRequest request) {
        if ("BILIBILI".equalsIgnoreCase(request.provider())
                && Set.of("MEME", "IMAGE").contains(request.assetType().toUpperCase(Locale.ROOT))) {
            throw new IllegalStateException("Bilibili 搜索结果属于视频候选，不能冒充 Meme 或普通图片；请改选视频类型");
        }
        AssetSearchExpansion expansion = queryExpander.expand(request.query(), request.assetType());
        AssetSearchRequest providerRequest = new AssetSearchRequest(expansion.providerQuery(),
                request.assetType(), request.pageSize(), request.page(), request.commercialUse(),
                request.allowModification(), request.provider(), request.sort());
        Set<UUID> ids = Collections.synchronizedSet(new LinkedHashSet<>());
        List<String> failures = Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> searches = new ArrayList<>();
        int attemptedProviders = 0;
        // Bilibili is a rights-review platform candidate, not an open-license source.
        // It must be selected explicitly and must never crowd out open results.
        if ("BILIBILI".equalsIgnoreCase(request.provider())
                && bilibili.supports(request.assetType())) {
            attemptedProviders++;
            searches.add(CompletableFuture.runAsync(() -> discoverFrom("BILIBILI",
                    () -> bilibili.search(request), request, expansion, ids, failures), taskExecutor));
        }
        if (providerSelected(request.provider(), "OPENVERSE")
                && openverse.supports(request.assetType())) {
            attemptedProviders++;
            searches.add(CompletableFuture.runAsync(() ->
                    discoverFrom("OPENVERSE", () -> openverse.search(providerRequest), request, expansion, ids, failures),
                    taskExecutor));
        }
        if (providerSelected(request.provider(), "WIKIMEDIA")
                && wikimedia.supports(request.assetType())) {
            attemptedProviders++;
            searches.add(CompletableFuture.runAsync(() ->
                    discoverFrom("WIKIMEDIA", () -> wikimedia.search(providerRequest), request, expansion, ids, failures),
                    taskExecutor));
        }
        if (providerSelected(request.provider(), "PEXELS")
                && pexels.supports(request.assetType())) {
            attemptedProviders++;
            searches.add(CompletableFuture.runAsync(() ->
                    discoverFrom("PEXELS", () -> pexels.search(providerRequest), request, expansion, ids, failures),
                    taskExecutor));
        }
        if (providerSelected(request.provider(), "PIXABAY")
                && pixabay.supports(request.assetType())) {
            attemptedProviders++;
            searches.add(CompletableFuture.runAsync(() ->
                    discoverFrom("PIXABAY", () -> pixabay.search(providerRequest), request, expansion, ids, failures),
                    taskExecutor));
        }
        requireConfiguredProvider(request);
        CompletableFuture.allOf(searches.toArray(CompletableFuture[]::new)).join();
        if (attemptedProviders == 0 || failures.size() == attemptedProviders) {
            List<AssetView> cached = lexicalList(request.assetType(), request.query(), request.provider(), null, null, false, "newest");
            if (!cached.isEmpty()) return cached;
            throw new IllegalStateException(attemptedProviders == 0
                    ? "当前公共素材源仍在恢复或尚未配置，请稍后重试或使用下方原站检索"
                    : "公共素材源连接失败：" + String.join("、", failures));
        }
        scheduleChineseAi(ids);
        Map<String, AssetView> unique = new LinkedHashMap<>();
        ids.stream().map(this::find)
                .sorted(java.util.Comparator.comparingInt(asset -> providerPriority(asset.provider())))
                .forEach(asset -> unique.putIfAbsent(assetDeduplicationKey(asset), asset));
        return new ArrayList<>(unique.values());
    }

    private boolean providerSelected(String requestedProvider, String provider) {
        return requestedProvider == null || requestedProvider.isBlank()
                || provider.equalsIgnoreCase(requestedProvider);
    }

    public boolean supportsProvider(String provider, String assetType) {
        if (provider == null || assetType == null) return false;
        return switch (provider.toUpperCase(Locale.ROOT)) {
            case "OPENVERSE" -> openverse.supports(assetType);
            case "WIKIMEDIA" -> wikimedia.supports(assetType);
            case "BILIBILI" -> bilibili.supports(assetType);
            case "PEXELS" -> pexels.supports(assetType);
            case "PIXABAY" -> pixabay.supports(assetType);
            default -> false;
        };
    }

    private void requireConfiguredProvider(AssetSearchRequest request) {
        if ("PEXELS".equalsIgnoreCase(request.provider()) && !pexels.configured()) {
            throw new IllegalStateException("Pexels 搜索尚未配置：请设置 PEXELS_API_KEY");
        }
        if ("PIXABAY".equalsIgnoreCase(request.provider()) && !pixabay.configured()) {
            throw new IllegalStateException("Pixabay 搜索尚未配置：请设置 PIXABAY_API_KEY");
        }
    }

    private void discoverFrom(String provider, java.util.function.Supplier<JsonNode> search,
                              AssetSearchRequest request, AssetSearchExpansion expansion,
                              Set<UUID> ids, List<String> failures) {
        JsonNode response;
        try {
            response = search.get();
        } catch (Exception exception) {
            failures.add(provider);
            log.warn("Open asset provider {} is temporarily unavailable: {}", provider, concise(exception));
            return;
        }
        // Network calls run concurrently, but catalog writes are serialized so H2 and tag upserts
        // never race across provider threads. Transaction context is intentionally not assumed here.
        synchronized (this) {
            for (JsonNode item : response.path("results")) {
                if ("MEME".equalsIgnoreCase(request.assetType()) && !isMeme(item)) continue;
                UUID id = upsert(item, request.assetType().toUpperCase(Locale.ROOT), provider);
                ids.add(id);
                List<String> sourceTags = new ArrayList<>();
                item.path("tags").forEach(tag -> {
                    String value = tag.isObject() ? tag.path("name").asText() : tag.asText();
                    if (!value.isBlank()) sourceTags.add(value);
                });
                assignTags(id, sourceTags.stream().limit(20).toList(), "SOURCE", 1.0, null);
                assignTags(id, expansion.chineseTags(), "QUERY", 0.9, currentUser.userId());
                assignTags(id, aiTagger.classifyFast(request.assetType(), item.path("title").asText(), sourceTags),
                        "AI", 0.65, null);
            }
        }
    }

    private boolean isMeme(JsonNode item) {
        StringBuilder text = new StringBuilder(item.path("title").asText()).append(' ');
        item.path("tags").forEach(tag -> text.append(tag.isObject() ? tag.path("name").asText() : tag.asText()).append(' '));
        String value = text.toString().toLowerCase(Locale.ROOT);
        return java.util.regex.Pattern.compile("(^|\\W)(meme|reaction|sticker|emoji|emoticon|wojak|rage face|image macro)(\\W|$)")
                .matcher(value).find()
                || value.contains("表情包") || value.contains("梗图") || value.contains("斗图") || value.contains("表情图");
    }

    private String assetDeduplicationKey(AssetView asset) {
        String landing = asset.landingUrl() == null ? "" : asset.landingUrl().replaceFirst("[?#].*$", "").toLowerCase(Locale.ROOT);
        if (!landing.isBlank()) return "url:" + landing;
        String title = asset.title() == null ? "" : asset.title().replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
        String creator = asset.creator() == null ? "" : asset.creator().strip().toLowerCase(Locale.ROOT);
        return "text:" + title + '|' + creator;
    }

    private int providerPriority(String provider) {
        int index = assetLibraryProperties.getProviderPriority().indexOf(provider);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private String concise(Exception exception) {
        String message = exception.getMessage();
        if (message == null) return exception.getClass().getSimpleName();
        if (message.contains("412")) return "HTTP 412 platform risk control";
        return message.length() <= 240 ? message : message.substring(0, 240) + "…";
    }

    public List<AssetView> list(String assetType, String query) {
        return list(assetType, query, null, null, null, false, "newest");
    }

    public List<AssetView> similar(UUID assetId) {
        AssetView anchor = find(assetId);
        return semanticSearch.similarTo(anchor,
                list(anchor.assetType(), "", null, null, null, false, "newest"));
    }

    public int repairBilibiliMetadata() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,landing_url FROM external_asset
                WHERE provider='BILIBILI' AND landing_url LIKE '%/video/BV%'
                ORDER BY discovered_at DESC LIMIT 100
                """);
        int repaired = 0;
        for (Map<String, Object> row : rows) {
            UUID id = (UUID) row.get("ID");
            String sourceUrl = String.valueOf(row.get("LANDING_URL"));
            String bvid = extractBvid(sourceUrl);
            if (bvid == null) continue;
            try {
                BilibiliAssetClient.VideoMetadata metadata = bilibili.metadata(bvid);
                if (metadata.title().isBlank()) continue;
                jdbc.update("""
                        UPDATE external_asset SET title=?,localized_title=?,creator=?,duration_ms=?,
                          preview_url=COALESCE(?,preview_url) WHERE id=?
                        """, metadata.title(), metadata.title(), metadata.creator(), metadata.durationMs(),
                        metadata.thumbnailUrl(), id);
                jdbc.update("DELETE FROM asset_tag_assignment WHERE asset_id=? AND tag_source <> 'USER'", id);
                jdbc.update("DELETE FROM asset_embedding WHERE asset_id=?", id);
                assignTags(id, metadata.tags(), "SOURCE", 1.0, null);
                repaired++;
            } catch (Exception exception) {
                log.warn("Bilibili metadata repair skipped bvid={}: {}", bvid, concise(exception));
            }
        }
        return repaired;
    }

    public List<AssetView> list(String assetType, String query, String provider, String importStatus,
                                Boolean favorite, boolean archived, String sort) {
        return list(assetType, query, provider, importStatus, favorite, archived, sort, true);
    }

    public List<AssetView> list(String assetType, String query, String provider, String importStatus,
                                Boolean favorite, boolean archived, String sort, boolean semanticEnabled) {
        return list(assetType, query, provider, importStatus, favorite, archived, sort, semanticEnabled, 100);
    }

    public List<AssetView> list(String assetType, String query, String provider, String importStatus,
                                Boolean favorite, boolean archived, String sort, boolean semanticEnabled,
                                int requestedLimit) {
        int limit = Math.max(1, Math.min(60, requestedLimit));
        if (semanticEnabled && query != null && !query.isBlank()) {
            List<AssetView> semantic = semanticSearch.rerank(query,
                    lexicalList(assetType, "", provider, importStatus, favorite, archived, sort,
                            Math.min(100, Math.max(60, limit * 3))));
            if (!semantic.isEmpty()) return semantic.stream().limit(limit).toList();
        }
        return lexicalList(assetType, query, provider, importStatus, favorite, archived, sort, limit);
    }

    private List<AssetView> lexicalList(String assetType, String query, String provider, String importStatus,
                                        Boolean favorite, boolean archived, String sort) {
        return lexicalList(assetType, query, provider, importStatus, favorite, archived, sort, 100);
    }

    private List<AssetView> lexicalList(String assetType, String query, String provider, String importStatus,
                                        Boolean favorite, boolean archived, String sort, int requestedLimit) {
        int limit = Math.max(1, Math.min(100, requestedLimit));
        String type = assetType == null || assetType.isBlank() ? "%" : assetType.toUpperCase(Locale.ROOT);
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String providerFilter = provider == null || provider.isBlank() ? "%" : provider.toUpperCase(Locale.ROOT);
        String statusFilter = importStatus == null || importStatus.isBlank() ? "%" : importStatus.toUpperCase(Locale.ROOT);
        String order = switch (String.valueOf(sort).toLowerCase(Locale.ROOT)) {
            case "oldest" -> "asset.discovered_at ASC";
            case "title" -> "LOWER(asset.title) ASC";
            default -> "asset.discovered_at DESC";
        };
        String sql = """
                SELECT DISTINCT asset.id, asset.discovered_at FROM external_asset asset
                WHERE asset.asset_type LIKE ? AND asset.provider LIKE ? AND asset.import_status LIKE ?
                  AND asset.archived=? AND (? IS NULL OR asset.favorite=?) AND (
                    LOWER(asset.title) LIKE ? OR LOWER(COALESCE(asset.localized_title,'')) LIKE ?
                    OR LOWER(COALESCE(asset.creator,'')) LIKE ?
                    OR EXISTS (
                        SELECT 1 FROM asset_tag_assignment assignment
                        JOIN asset_tag tag ON tag.id=assignment.tag_id
                        WHERE assignment.asset_id=asset.id
                          AND LOWER(tag.display_name) LIKE ?
                          AND NOT EXISTS (
                              SELECT 1 FROM asset_tag_override override_tag
                              WHERE override_tag.asset_id=asset.id
                                AND override_tag.tag_id=tag.id
                                AND override_tag.user_id=?
                                AND override_tag.action='REMOVE'
                          )
                    )
                    OR EXISTS (
                        SELECT 1 FROM asset_tag_override override_tag
                        JOIN asset_tag tag ON tag.id=override_tag.tag_id
                        WHERE override_tag.asset_id=asset.id
                          AND override_tag.user_id=?
                          AND override_tag.action='ADD'
                          AND LOWER(tag.display_name) LIKE ?
                    )
                )
                ORDER BY """ + " " + order + " LIMIT " + limit;
        List<AssetView> results = jdbc.query(sql, (rs, n) -> find(rs.getObject(1, UUID.class)), type,
                providerFilter, statusFilter, archived, favorite, favorite,
                "%" + text + "%", "%" + text + "%", "%" + text + "%", "%" + text + "%",
                currentUser.userId(), currentUser.userId(), "%" + text + "%");
        scheduleChineseAi(results.stream().map(AssetView::id).toList());
        return results;
    }

    @Transactional
    public AssetView updateState(UUID assetId, AssetStateUpdateRequest request) {
        requireAsset(assetId);
        if (request.favorite() != null) {
            jdbc.update("UPDATE external_asset SET favorite=? WHERE id=?", request.favorite(), assetId);
        }
        if (request.archived() != null) {
            jdbc.update("UPDATE external_asset SET archived=? WHERE id=?", request.archived(), assetId);
        }
        return find(assetId);
    }

    @Transactional
    public AssetView registerReference(AssetReferenceRequest request) {
        URI sourceUri = URI.create(request.sourceUrl());
        validatePublicHttps(sourceUri);
        if (request.previewUrl() != null && !request.previewUrl().isBlank()) {
            validatePublicHttps(URI.create(request.previewUrl()));
        }
        if (request.downloadUrl() != null && !request.downloadUrl().isBlank()) {
            validatePublicHttps(URI.create(request.downloadUrl()));
        }
        String provider = request.provider() == null || request.provider().isBlank()
                ? providerFor(sourceUri.getHost()) : request.provider().toUpperCase(Locale.ROOT);
        String title = cleanReferenceTitle(provider, request.title(), request.sourceUrl());
        boolean repairedBilibiliTitle = !title.equals(request.title().trim());
        String creator = request.creator();
        String previewUrl = request.previewUrl();
        Long durationMs = null;
        List<String> importedTags = request.platformTags() == null ? List.of() : request.platformTags();
        if ("BILIBILI".equals(provider)) {
            String bvid = extractBvid(request.sourceUrl());
            if (bvid != null) {
                try {
                    BilibiliAssetClient.VideoMetadata metadata = bilibili.metadata(bvid);
                    title = metadata.title();
                    creator = metadata.creator();
                    previewUrl = metadata.thumbnailUrl();
                    durationMs = metadata.durationMs();
                    importedTags = metadata.tags();
                    repairedBilibiliTitle = false;
                } catch (Exception exception) {
                    log.warn("Bilibili reference metadata lookup failed bvid={}: {}", bvid, concise(exception));
                }
            }
        }
        String externalId = UUID.nameUUIDFromBytes(request.sourceUrl().getBytes(StandardCharsets.UTF_8)).toString();
        List<UUID> existing = jdbc.query(
                "SELECT id FROM external_asset WHERE provider=? AND external_id=?",
                (rs, n) -> rs.getObject(1, UUID.class), provider, externalId);
        UUID id;
        if (!existing.isEmpty()) {
            id = existing.getFirst();
        } else if (!"USER_REFERENCE".equals(provider)) {
            List<UUID> legacy = jdbc.query(
                    "SELECT id FROM external_asset WHERE provider='USER_REFERENCE' AND landing_url=?",
                    (rs, n) -> rs.getObject(1, UUID.class), request.sourceUrl());
            id = legacy.isEmpty() ? UUID.randomUUID() : legacy.getFirst();
            if (!legacy.isEmpty()) {
                jdbc.update("UPDATE external_asset SET provider=?,external_id=? WHERE id=?", provider, externalId, id);
            }
        } else {
            id = UUID.randomUUID();
        }
        String metadata;
        try {
            metadata = objectMapper.writeValueAsString(Map.of(
                    "registeredBy", currentUser.userId().toString(),
                    "sourceUrl", request.sourceUrl(),
                    "platformTags", request.platformTags() == null ? List.of() : request.platformTags()));
        } catch (Exception exception) {
            metadata = "{}";
        }
        jdbc.update("""
                MERGE INTO external_asset(id,provider,external_id,asset_type,title,creator,landing_url,
                preview_url,download_url,license_code,license_url,attribution,duration_ms,local_path,
                import_status,metadata_json,discovered_at,downloaded_at) KEY(provider,external_id)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,NULL,?,?,?,NULL)
                """, id, provider, externalId, request.assetType(), title, creator,
                request.sourceUrl(), previewUrl, request.downloadUrl(), request.licenseCode(), request.licenseUrl(),
                request.attribution(), durationMs, request.downloadUrl() == null ? "REFERENCE_ONLY" : "DISCOVERED",
                metadata, OffsetDateTime.now());
        List<String> allTags = importedTags;
        List<String> originTags = allTags.stream().filter(tag -> tag.startsWith("来源判断:")).toList();
        List<String> platformTags = allTags.stream().filter(tag -> !tag.startsWith("来源判断:")).toList();
        String tagSource = Set.of("BILIBILI", "OPENVERSE", "WIKIMEDIA").contains(provider) ? "SOURCE" : "PLATFORM";
        if ("BILIBILI".equals(provider)) {
            jdbc.update("DELETE FROM asset_tag_assignment WHERE asset_id=? AND tag_source <> 'USER'", id);
        }
        assignTags(id, platformTags, tagSource, 1.0, currentUser.userId());
        assignTags(id, originTags, "AI_ORIGIN", 0.55, null);
        if (repairedBilibiliTitle) {
            jdbc.update("DELETE FROM asset_tag_assignment WHERE asset_id=? AND tag_source IN ('AI','AI_TRANSLATION')", id);
            jdbc.update("DELETE FROM asset_embedding WHERE asset_id=?", id);
            jdbc.update("UPDATE external_asset SET localized_title=NULL WHERE id=?", id);
        }
        if (!"BILIBILI".equals(provider)) {
            assignTags(id, aiTagger.classifyFast(request.assetType(), title, platformTags),
                    "AI", 0.65, null);
            enrichWithChineseAi(List.of(id));
        }
        return find(id);
    }

    @Transactional
    public AssetView registerImportedMedia(ImportedMediaAsset imported, Path localPath) {
        URI sourceUri = URI.create(imported.sourceUrl());
        validatePublicHttps(sourceUri);
        Path resolvedPath = localPath.toAbsolutePath().normalize();
        Path importRoot = storageRoot.resolve("imports").normalize();
        if (!resolvedPath.startsWith(importRoot) || !Files.isRegularFile(resolvedPath)) {
            throw new IllegalArgumentException("导入素材文件不在授权目录中");
        }
        String provider = providerFor(sourceUri.getHost());
        String externalId = UUID.nameUUIDFromBytes(
                imported.sourceUrl().getBytes(StandardCharsets.UTF_8)).toString();
        List<UUID> existing = jdbc.query(
                "SELECT id FROM external_asset WHERE provider=? AND external_id=?",
                (rs, n) -> rs.getObject(1, UUID.class), provider, externalId);
        UUID id = existing.isEmpty() ? UUID.randomUUID() : existing.getFirst();
        String title = imported.title() == null || imported.title().isBlank()
                ? resolvedPath.getFileName().toString() : imported.title().trim();
        String preview = normalizePreviewUrl(imported.previewUrl());
        List<String> tags = imported.platformTags() == null ? List.of() : imported.platformTags();
        List<String> originTags = tags.stream().filter(tag -> tag.startsWith("来源判断:")).toList();
        List<String> platformTags = tags.stream().filter(tag -> !tag.startsWith("来源判断:")).toList();
        String metadata;
        try {
            metadata = objectMapper.writeValueAsString(Map.of(
                    "registeredBy", currentUser.userId().toString(),
                    "sourceUrl", imported.sourceUrl(),
                    "rightsConfirmed", true,
                    "sizeBytes", Files.size(resolvedPath),
                    "platformTags", tags));
        } catch (Exception exception) {
            throw new IllegalStateException("无法保存导入素材元数据", exception);
        }
        Long durationMs = imported.durationSeconds() == null ? null
                : Math.max(0L, Math.round(imported.durationSeconds() * 1000));
        jdbc.update("""
                MERGE INTO external_asset(id,provider,external_id,asset_type,title,creator,landing_url,
                preview_url,download_url,license_code,license_url,attribution,duration_ms,local_path,
                import_status,metadata_json,discovered_at,downloaded_at) KEY(provider,external_id)
                VALUES(?,?,?,?,?,?,?,?,NULL,'USER_AUTHORIZED',NULL,?,?,?,?,?,?,?)
                """, id, provider, externalId, "VIDEO", title, imported.creator(), imported.sourceUrl(),
                preview, "用户确认拥有下载和再创作所需权利", durationMs, resolvedPath.toString(),
                "DOWNLOADED", metadata, OffsetDateTime.now(), OffsetDateTime.now());
        assignTags(id, platformTags.stream().limit(20).toList(), "PLATFORM", 1.0, currentUser.userId());
        assignTags(id, originTags, "AI_ORIGIN", 0.55, null);
        assignTags(id, aiTagger.classify("VIDEO", title, platformTags), "AI", 0.65, null);
        enrichWithChineseAi(List.of(id));
        return find(id);
    }

    @Transactional
    public AssetView uploadLocal(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择要上传的素材文件");
        if (file.getSize() > MAX_UPLOAD_BYTES) throw new IllegalArgumentException("单个素材不能超过 500MB");
        String original = file.getOriginalFilename() == null ? "local-asset" : file.getOriginalFilename();
        String safeName = Path.of(original).getFileName().toString().replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = extension(safeName).toLowerCase(Locale.ROOT);
        String assetType = localAssetType(contentType, extension);
        UUID id = UUID.randomUUID();
        Path directory = storageRoot.resolve("library").resolve(id.toString()).normalize();
        if (!directory.startsWith(storageRoot)) throw new IllegalStateException("素材存储目录配置无效");
        Path destination = directory.resolve(safeName).normalize();
        if (!destination.startsWith(directory)) throw new IllegalArgumentException("素材文件名无效");
        try {
            Files.createDirectories(directory);
            try (InputStream input = file.getInputStream()) { Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception exception) {
            throw new IllegalStateException("保存本地素材失败：" + exception.getMessage(), exception);
        }
        boolean greenScreen = "VIDEO".equals(assetType)
                && safeName.toLowerCase(Locale.ROOT).matches(".*(green[ _-]?screen|chroma|greenscreen).*|.*绿幕.*");
        Path stored = greenScreen ? createCutout(destination, directory) : destination;
        String metadata;
        try {
            metadata = objectMapper.writeValueAsString(Map.of("originalName", original, "sizeBytes", file.getSize(),
                    "contentType", contentType, "greenScreenDetected", greenScreen, "cutoutApplied", greenScreen));
        } catch (Exception exception) { throw new IllegalStateException("无法保存素材元数据", exception); }
        jdbc.update("""
                INSERT INTO external_asset(id,provider,external_id,asset_type,title,creator,landing_url,
                license_code,attribution,local_path,import_status,metadata_json,discovered_at,downloaded_at)
                VALUES(?,'LOCAL_UPLOAD',?,?,?,?,?,'USER_AUTHORIZED',?,?,?,?,?,?)
                """, id, id.toString(), assetType, safeName, "本地用户", "/api/assets/" + id + "/preview",
                "用户从本地拖拽上传", stored.toString(), "DOWNLOADED", metadata, OffsetDateTime.now(), OffsetDateTime.now());
        List<String> tags = new ArrayList<>(aiTagger.classifyFast(assetType, safeName, List.of("本地上传")));
        tags.add("本地上传");
        if (greenScreen) { tags.add("绿幕"); tags.add("已抠图"); tags.add("主体素材"); }
        assignTags(id, tags, greenScreen ? "AI" : "USER", greenScreen ? 0.95 : 0.8,
                greenScreen ? null : currentUser.userId());
        return find(id);
    }

    private String localAssetType(String contentType, String extension) {
        if (contentType.startsWith("video/") || Set.of("mp4","mov","mkv","webm","avi").contains(extension)) return "VIDEO";
        if (contentType.startsWith("image/") || Set.of("png","jpg","jpeg","gif","webp").contains(extension)) return "IMAGE";
        if (contentType.startsWith("audio/") || Set.of("mp3","wav","ogg","m4a","flac","aac").contains(extension)) return "SFX";
        throw new IllegalArgumentException("仅支持视频、图片和音频素材");
    }

    private Path createCutout(Path source, Path directory) {
        Path output = directory.resolve("cutout-" + source.getFileName().toString().replaceFirst("\\.[^.]+$", "") + ".webm").normalize();
        try {
            var result = cn.longer233.gamenarrator.common.ExternalProcessRunner.run(List.of(
                    ffmpegCommand, "-nostdin", "-y", "-hide_banner", "-loglevel", "warning", "-threads", "0",
                    "-i", source.toString(), "-vf", "chromakey=0x00FF00:0.18:0.08,format=yuva420p",
                    "-an", "-c:v", "libvpx-vp9", "-deadline", "realtime", "-cpu-used", "6",
                    "-pix_fmt", "yuva420p", output.toString()), Duration.ofMinutes(2));
            if (result.exitCode() != 0 || !Files.isRegularFile(output))
                throw new IllegalStateException("FFmpeg 自动抠图失败：" + conciseOutput(result.output()));
            return output;
        } catch (cn.longer233.gamenarrator.common.ExternalProcessRunner.ProcessTimeoutException exception) {
            throw new IllegalStateException("自动抠图超时", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("自动抠图已中断", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("无法启动 FFmpeg 自动抠图：" + exception.getMessage(), exception);
        }
    }

    @Transactional
    public AssetView registerCompletedProject(UUID taskId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT name,status,rendered_video_path,generated_title,game_category,commentary_style,
                       planned_output_duration_seconds
                FROM video_tasks WHERE id=?
                """, taskId);
        if (rows.isEmpty()) throw new IllegalArgumentException("项目不存在：" + taskId);
        Map<String, Object> task = rows.getFirst();
        if (!"COMPLETED".equals(String.valueOf(task.get("STATUS")))) {
            throw new IllegalStateException("只有已完成并生成最终视频的项目才能加入素材库");
        }
        String renderedPath = text(task, "RENDERED_VIDEO_PATH");
        if (renderedPath == null || renderedPath.isBlank()) throw new IllegalStateException("项目没有可用的最终视频");
        Path source = Path.of(renderedPath).toAbsolutePath().normalize();
        if (!source.startsWith(storageRoot) || !Files.isRegularFile(source)) {
            throw new IllegalStateException("项目最终视频不存在或不在授权存储目录中");
        }
        Path projectDirectory = storageRoot.resolve("assets").resolve("projects").normalize();
        if (!projectDirectory.startsWith(storageRoot)) throw new IllegalStateException("素材目录配置无效");
        Path destination = projectDirectory.resolve(taskId + "." + extension(source.getFileName().toString())).normalize();
        if (!destination.startsWith(projectDirectory)) throw new IllegalStateException("素材文件路径无效");
        try {
            Files.createDirectories(projectDirectory);
            Files.copy(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("无法复制项目成片到素材库", exception);
        }

        String generatedTitle = text(task, "GENERATED_TITLE");
        String title = generatedTitle == null || generatedTitle.isBlank() ? text(task, "NAME") : generatedTitle;
        String externalId = taskId.toString();
        List<UUID> existing = jdbc.query("SELECT id FROM external_asset WHERE provider='PROJECT' AND external_id=?",
                (rs, n) -> rs.getObject(1, UUID.class), externalId);
        UUID assetId = existing.isEmpty() ? UUID.randomUUID() : existing.getFirst();
        Long durationMs = null;
        Object duration = task.get("PLANNED_OUTPUT_DURATION_SECONDS");
        if (duration instanceof Number number) durationMs = Math.max(0L, Math.round(number.doubleValue() * 1000));
        String metadata;
        try {
            metadata = objectMapper.writeValueAsString(Map.of("taskId", externalId, "source", "completed-project"));
        } catch (Exception exception) {
            throw new IllegalStateException("无法保存项目素材元数据", exception);
        }
        jdbc.update("""
                MERGE INTO external_asset(id,provider,external_id,asset_type,title,creator,landing_url,
                preview_url,download_url,license_code,license_url,attribution,duration_ms,local_path,
                import_status,metadata_json,discovered_at,downloaded_at) KEY(provider,external_id)
                VALUES(?,'PROJECT',?,'VIDEO',?,'GameNarrator',?,NULL,NULL,'USER_AUTHORIZED',NULL,?,?,?,?,?,?,?)
                """, assetId, externalId, title, "/api/tasks/" + taskId + "/preview",
                "用户创建并加入素材库的已完成项目", durationMs, destination.toString(), "DOWNLOADED",
                metadata, OffsetDateTime.now(), OffsetDateTime.now());
        List<String> tags = new ArrayList<>(List.of("已完成项目"));
        String category = text(task, "GAME_CATEGORY");
        String style = text(task, "COMMENTARY_STYLE");
        if (category != null && !category.isBlank()) tags.add(category);
        if (style != null && !style.isBlank()) tags.add(style);
        assignTags(assetId, tags, "PROJECT", 1.0, currentUser.userId());
        assignTags(assetId, aiTagger.classifyFast("VIDEO", title, tags), "AI", 0.65, null);
        return find(assetId);
    }

    @Transactional
    public AssetView updateTags(UUID assetId, AssetTagUpdateRequest request) {
        requireAsset(assetId);
        if (request.add() != null) {
            for (String tag : request.add()) override(assetId, tag, "ADD");
        }
        if (request.remove() != null) {
            for (String tag : request.remove()) override(assetId, tag, "REMOVE");
        }
        return find(assetId);
    }

    @Transactional
    public AssetView download(UUID assetId) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT download_url,license_code,title FROM external_asset WHERE id=?", assetId);
        String license = String.valueOf(row.get("LICENSE_CODE")).toLowerCase(Locale.ROOT);
        if (!Set.of("cc0", "pdm", "by", "by-sa", "pexels_license", "pixabay_content_license").contains(license)) {
            throw new IllegalStateException("该素材许可证不在自动下载白名单中，请在原始页面人工确认");
        }
        URI uri = URI.create(String.valueOf(row.get("DOWNLOAD_URL")));
        validatePublicHttps(uri);
        try {
            Path directory = storageRoot.resolve("library").resolve(assetId.toString());
            Files.createDirectories(directory);
            String extension = extension(uri.getPath());
            Path output = directory.resolve("source." + extension);
            HttpURLConnection connection = remoteConnector.open(uri,
                    Map.of("User-Agent", "GameNarrator/0.1"), 3);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("素材下载返回 HTTP " + status);
            long length = connection.getContentLengthLong();
            if (length > MAX_DOWNLOAD_BYTES) throw new IllegalStateException("素材超过 100MB 自动下载限制");
            try (InputStream input = connection.getInputStream();
                 var outputStream = Files.newOutputStream(output)) {
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    total += count;
                    if (total > MAX_DOWNLOAD_BYTES) throw new IllegalStateException("素材超过 100MB 自动下载限制");
                    outputStream.write(buffer, 0, count);
                }
            }
            jdbc.update("""
                    UPDATE external_asset SET local_path=?,import_status='DOWNLOADED',downloaded_at=? WHERE id=?
                    """, output.toString(), OffsetDateTime.now(), assetId);
            return find(assetId);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("素材下载失败：" + exception.getMessage(), exception);
        }
    }

    @Transactional
    public AssetView derive(UUID assetId, AssetDerivativeRequest request) {
        AssetView source = find(assetId);
        if (!"VIDEO".equals(source.assetType())) throw new IllegalArgumentException("只有视频素材可以提取画面或声音");
        if (!"DOWNLOADED".equals(source.importStatus())) source = download(assetId);
        Path input = Path.of(source.localPath()).toAbsolutePath().normalize();
        Path directory = storageRoot.resolve("library").resolve(assetId.toString()).normalize();
        if (!input.startsWith(storageRoot) || !directory.startsWith(storageRoot) || !Files.isRegularFile(input)) {
            throw new IllegalStateException("视频文件不在授权素材目录中");
        }
        String mode = request.mode().toUpperCase(Locale.ROOT);
        double timestamp = request.timestampSeconds() == null ? 0 : request.timestampSeconds();
        String suffix = "FRAME".equals(mode) ? "frame-" + Math.round(timestamp * 1000) + ".jpg" : "audio.m4a";
        Path output = directory.resolve(suffix).normalize();
        List<String> command = new ArrayList<>(List.of(ffmpegCommand, "-y"));
        if ("FRAME".equals(mode)) command.addAll(List.of("-ss", String.valueOf(timestamp)));
        command.addAll(List.of("-i", input.toString()));
        if ("FRAME".equals(mode)) command.addAll(List.of("-frames:v", "1", "-q:v", "2", output.toString()));
        else command.addAll(List.of("-vn", "-c:a", "aac", "-b:a", "192k", output.toString()));
        try {
            var result = cn.longer233.gamenarrator.common.ExternalProcessRunner.run(command,
                    java.time.Duration.ofMinutes(10));
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
        String title = source.title() + ("FRAME".equals(mode) ? " · 单帧" : " · 音轨");
        jdbc.update("""
                MERGE INTO external_asset(id,provider,external_id,asset_type,title,creator,landing_url,
                preview_url,download_url,license_code,license_url,attribution,duration_ms,local_path,
                import_status,metadata_json,discovered_at,downloaded_at) KEY(provider,external_id)
                VALUES(?,?,?,?,?,?,?,?,NULL,?,?,?,?,?,?,?, ?,?)
                """, derivedId, "LOCAL_DERIVED", assetId + ":" + mode + ":" + timestamp, derivedType,
                title, source.creator(), source.landingUrl(), null, source.licenseCode(), source.licenseUrl(),
                source.attribution(), "FRAME".equals(mode) ? null : source.durationMs(), output.toString(),
                "DOWNLOADED", "{\"derivedFrom\":\"" + assetId + "\",\"mode\":\"" + mode + "\"}",
                OffsetDateTime.now(), OffsetDateTime.now());
        return find(derivedId);
    }

    private String conciseOutput(String output) {
        if (output == null) return "无输出";
        String value = output.strip();
        return value.length() <= 600 ? value : value.substring(value.length() - 600);
    }

    @Transactional
    public void delete(UUID assetId) {
        List<String> paths = jdbc.query("SELECT local_path FROM external_asset WHERE id=?",
                (rs, n) -> rs.getString(1), assetId);
        if (paths.isEmpty()) throw new IllegalArgumentException("素材不存在：" + assetId);
        jdbc.update("DELETE FROM asset_embedding WHERE asset_id=?", assetId);
        jdbc.update("DELETE FROM asset_tag_override WHERE asset_id=?", assetId);
        jdbc.update("DELETE FROM asset_tag_assignment WHERE asset_id=?", assetId);
        jdbc.update("DELETE FROM external_asset WHERE id=?", assetId);
        String localPath = paths.getFirst();
        if (localPath != null && !localPath.isBlank()) deleteOwnedFile(assetId, localPath);
    }

    private void deleteOwnedFile(UUID assetId, String value) {
        try {
            Path path = Path.of(value).toAbsolutePath().normalize();
            if (!path.startsWith(storageRoot) || path.equals(storageRoot)) {
                throw new IllegalArgumentException("素材文件不在授权存储目录中");
            }
            Path libraryDirectory = storageRoot.resolve("library").resolve(assetId.toString()).normalize();
            if (path.startsWith(libraryDirectory) && Files.isDirectory(libraryDirectory)) {
                try (var entries = Files.walk(libraryDirectory)) {
                    for (Path entry : entries.sorted(java.util.Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(entry);
                    }
                }
            } else {
                Files.deleteIfExists(path);
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("素材记录已删除，但本地文件清理失败：" + exception.getMessage(), exception);
        }
    }

    public AssetView find(UUID id) {
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM external_asset WHERE id=?", id);
        return new AssetView(id, text(row, "PROVIDER"), text(row, "ASSET_TYPE"), text(row, "TITLE"),
                text(row, "LOCALIZED_TITLE"),
                text(row, "CREATOR"), text(row, "LANDING_URL"), text(row, "PREVIEW_URL"), text(row, "DOWNLOAD_URL"),
                text(row, "LICENSE_CODE"), text(row, "LICENSE_URL"), text(row, "ATTRIBUTION"),
                row.get("DURATION_MS") == null ? null : ((Number) row.get("DURATION_MS")).longValue(),
                text(row, "IMPORT_STATUS"), text(row, "LOCAL_PATH"),
                Boolean.TRUE.equals(row.get("FAVORITE")), Boolean.TRUE.equals(row.get("ARCHIVED")), effectiveTags(id),
                (OffsetDateTime) row.get("DISCOVERED_AT"));
    }

    public Path previewFile(UUID assetId) {
        List<String> paths = jdbc.query("SELECT local_path FROM external_asset WHERE id=?",
                (rs, n) -> rs.getString(1), assetId);
        if (paths.isEmpty()) throw new IllegalArgumentException("素材不存在：" + assetId);
        String value = paths.getFirst();
        if (value == null || value.isBlank()) throw new IllegalStateException("该素材尚未下载，无法本地预览");
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!path.startsWith(storageRoot) || path.equals(storageRoot) || !Files.isRegularFile(path)) {
            throw new IllegalStateException("素材预览文件不存在或不在授权存储目录中");
        }
        return path;
    }

    public RemotePreviewSource remoteAudioPreview(UUID assetId) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT asset_type,title,download_url,preview_url,local_path
                FROM external_asset WHERE id=?
                """, assetId);
        String type = text(row, "ASSET_TYPE");
        if (!Set.of("SFX", "BGM").contains(type)) {
            throw new IllegalArgumentException("只有音效和音乐支持远程试听");
        }
        if (text(row, "LOCAL_PATH") != null) {
            throw new IllegalStateException("该素材已经下载，请使用本地预览接口");
        }
        String value = text(row, "DOWNLOAD_URL");
        if (value == null || value.isBlank()) value = text(row, "PREVIEW_URL");
        if (value == null || value.isBlank()) throw new IllegalStateException("该素材没有可用的试听地址");
        URI uri = URI.create(value);
        validatePublicHttps(uri);
        return new RemotePreviewSource(uri, text(row, "TITLE"));
    }

    public record RemotePreviewSource(URI uri, String title) { }

    public RemoteThumbnailSource remoteThumbnail(UUID assetId) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT preview_url,download_url,landing_url,provider,asset_type FROM external_asset WHERE id=?
                """, assetId);
        String preview = text(row, "PREVIEW_URL");
        if (preview == null || preview.isBlank()) throw new IllegalStateException("该素材没有可用的远程封面");
        URI uri = URI.create(preview);
        validatePublicHttps(uri);
        String referer = text(row, "LANDING_URL");
        if ("BILIBILI".equals(text(row, "PROVIDER"))) referer = "https://www.bilibili.com/";
        String fallback = "MEME".equals(text(row, "ASSET_TYPE")) ? text(row, "DOWNLOAD_URL") : null;
        if (fallback != null && !fallback.isBlank()) {
            URI fallbackUri = URI.create(fallback);
            validatePublicHttps(fallbackUri);
            fallback = fallbackUri.toString();
        }
        return new RemoteThumbnailSource(uri.toString(), fallback, referer);
    }

    public record RemoteThumbnailSource(String url, String fallbackUrl, String referer) { }

    private UUID upsert(JsonNode item, String assetType, String provider) {
        String externalId = item.path("id").asText();
        List<UUID> existing = jdbc.query("SELECT id FROM external_asset WHERE provider=? AND external_id=?",
                (rs, n) -> rs.getObject(1, UUID.class), provider, externalId);
        UUID id = existing.isEmpty() ? UUID.randomUUID() : existing.getFirst();
        String metadata;
        try { metadata = objectMapper.writeValueAsString(item); }
        catch (Exception exception) { metadata = "{}"; }
        boolean platformCandidate = Set.of("BILIBILI", "DOUYIN", "YOUTUBE", "TIKTOK").contains(provider);
        jdbc.update("""
                MERGE INTO external_asset(id,provider,external_id,asset_type,title,creator,landing_url,
                preview_url,download_url,license_code,license_url,attribution,duration_ms,local_path,
                import_status,metadata_json,discovered_at,downloaded_at) KEY(provider,external_id)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,NULL,?,?,?,NULL)
                """, id, provider, externalId, assetType, item.path("title").asText("未命名素材"),
                item.path("creator").asText(null), item.path("foreign_landing_url").asText(),
                first(item, "thumbnail", "url"), platformCandidate ? null : item.path("url").asText(null),
                item.path("license").asText("unknown"), item.path("license_url").asText(null),
                item.path("attribution").asText(null), item.path("duration").isNumber()
                        ? item.path("duration").asLong() : null,
                platformCandidate ? "REFERENCE_ONLY" : "DISCOVERED", metadata, OffsetDateTime.now());
        return id;
    }

    private void assignTags(UUID assetId, List<String> tags, String source, double confidence, UUID userId) {
        for (String raw : tags) {
            String normalized = normalize(raw);
            if (normalized.isBlank()) continue;
            UUID tagId = ensureTag(normalized, raw.trim());
            jdbc.update("""
                    MERGE INTO asset_tag_assignment(id,asset_id,tag_id,tag_source,confidence,created_by,created_at)
                    KEY(asset_id,tag_id,tag_source) VALUES(?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), assetId, tagId, source, confidence, userId, OffsetDateTime.now());
        }
    }

    private void enrichWithChineseAi(Collection<UUID> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) return;
        List<UUID> pending = assetIds.stream().filter(id -> jdbc.queryForObject(
                "SELECT localized_title IS NULL FROM external_asset WHERE id=?", Boolean.class, id))
                .limit(20).toList();
        if (pending.isEmpty()) return;
        List<AssetView> assets = pending.stream().map(this::find).toList();
        List<AiAssetTagger.AssetAiInput> inputs = assets.stream().map(asset ->
                new AiAssetTagger.AssetAiInput(asset.title(), asset.assetType(),
                        asset.tags().stream().map(AssetView.TagView::name).toList())).toList();
        List<AiAssetTagger.AssetAiAnalysis> analyses = aiTagger.analyzeBatch(inputs);
        for (int i = 0; i < Math.min(pending.size(), analyses.size()); i++) {
            UUID id = pending.get(i);
            AiAssetTagger.AssetAiAnalysis analysis = analyses.get(i);
            jdbc.update("UPDATE external_asset SET localized_title=? WHERE id=?", analysis.chineseTitle(), id);
            assignTags(id, analysis.translatedTags(), "AI_TRANSLATION", 0.75, null);
            assignTags(id, analysis.analysisTags(), "AI", 0.7, null);
            jdbc.update("DELETE FROM asset_embedding WHERE asset_id=?", id);
        }
    }

    private void scheduleChineseAi(Collection<UUID> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) return;
        List<UUID> batch = assetIds.stream().filter(localizationQueued::add).limit(20).toList();
        if (batch.isEmpty()) return;
        taskExecutor.execute(() -> {
            try {
                enrichWithChineseAi(batch);
            } catch (Exception exception) {
                log.warn("Background asset localization failed: {}", concise(exception));
            } finally {
                localizationQueued.removeAll(batch);
            }
        });
    }

    private void override(UUID assetId, String raw, String action) {
        String normalized = normalize(raw);
        if (normalized.isBlank()) return;
        UUID tagId = ensureTag(normalized, raw.trim());
        jdbc.update("""
                MERGE INTO asset_tag_override(id,asset_id,tag_id,action,user_id,created_at)
                KEY(asset_id,tag_id,user_id) VALUES(?,?,?,?,?,?)
                """, UUID.randomUUID(), assetId, tagId, action, currentUser.userId(), OffsetDateTime.now());
    }

    private List<AssetView.TagView> effectiveTags(UUID assetId) {
        return jdbc.query("""
                SELECT t.normalized_name,t.display_name,
                LISTAGG(DISTINCT a.tag_source, ',') sources,
                MAX(CASE WHEN o.action='ADD' THEN 1 ELSE 0 END) user_added,
                MAX(CASE WHEN o.action='REMOVE' THEN 1 ELSE 0 END) user_removed
                FROM asset_tag t
                LEFT JOIN asset_tag_assignment a ON a.tag_id=t.id AND a.asset_id=?
                LEFT JOIN asset_tag_override o ON o.tag_id=t.id AND o.asset_id=? AND o.user_id=?
                WHERE a.id IS NOT NULL OR o.id IS NOT NULL
                GROUP BY t.normalized_name,t.display_name
                HAVING MAX(CASE WHEN o.action='REMOVE' THEN 1 ELSE 0 END)=0
                ORDER BY user_added DESC,t.display_name
                """, (rs, n) -> new AssetView.TagView(rs.getString("display_name"),
                rs.getString("sources") == null ? List.of() : List.of(rs.getString("sources").split(",")),
                rs.getInt("user_added") == 1), assetId, assetId, currentUser.userId());
    }

    private UUID ensureTag(String normalized, String display) {
        List<UUID> ids = jdbc.query("SELECT id FROM asset_tag WHERE normalized_name=?",
                (rs, n) -> rs.getObject(1, UUID.class), normalized);
        if (!ids.isEmpty()) return ids.getFirst();
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO asset_tag(id,normalized_name,display_name,created_at) VALUES(?,?,?,?)",
                    id, normalized, display.substring(0, Math.min(100, display.length())), OffsetDateTime.now());
            return id;
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            return jdbc.queryForObject("SELECT id FROM asset_tag WHERE normalized_name=?", UUID.class, normalized);
        }
    }

    private void requireAsset(UUID id) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM external_asset WHERE id=?", Integer.class, id) == 0)
            throw new IllegalArgumentException("素材不存在");
    }

    private void validatePublicHttps(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
            throw new IllegalArgumentException("只允许下载 HTTPS 素材地址");
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                    throw new IllegalArgumentException("禁止访问本地或内网素材地址");
                }
            }
        } catch (java.net.UnknownHostException exception) {
            throw new IllegalArgumentException("素材地址无法解析");
        }
    }

    private String first(JsonNode node, String first, String second) {
        String value = node.path(first).asText();
        return value.isBlank() ? node.path(second).asText(null) : value;
    }

    private String extension(String path) {
        int dot = path.lastIndexOf('.');
        String value = dot < 0 ? "bin" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
        return value.matches("[a-z0-9]{1,6}") ? value : "bin";
    }

    private String providerFor(String host) {
        String value = host == null ? "" : host.toLowerCase(Locale.ROOT);
        if (value.endsWith("bilibili.com") || value.endsWith("b23.tv")) return "BILIBILI";
        if (value.endsWith("youtube.com") || value.endsWith("youtu.be")) return "YOUTUBE";
        if (value.endsWith("douyin.com")) return "DOUYIN";
        if (value.endsWith("tiktok.com")) return "TIKTOK";
        return "USER_REFERENCE";
    }

    static String cleanReferenceTitle(String provider, String rawTitle, String sourceUrl) {
        String title = rawTitle == null ? "" : rawTitle.replaceAll("\\s+", " ").trim();
        if (!"BILIBILI".equals(provider)) return title;
        boolean interfaceText = title.matches("(?i)^(?:添加至)?稍后再看.*")
                || title.matches("^[\\d.]+(?:万|亿)?\\s*[\\d.]+(?:万|亿)?\\s*\\d{1,2}:\\d{2}$");
        if (!interfaceText) return title;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)/video/(BV[0-9A-Za-z]+)")
                .matcher(sourceUrl);
        return matcher.find() ? "Bilibili 视频 " + matcher.group(1) : "Bilibili 视频候选素材";
    }

    private String extractBvid(String sourceUrl) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)/video/(BV[0-9A-Za-z]+)")
                .matcher(sourceUrl == null ? "" : sourceUrl);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String normalizePreviewUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.startsWith("http://") ? "https://" + value.substring(7) : value;
        validatePublicHttps(URI.create(normalized));
        return normalized;
    }

    private String normalize(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[#，,;；]+", "");
        return normalized.substring(0, Math.min(100, normalized.length()));
    }

    private String text(Map<String, Object> row, String key) {
        return row.get(key) == null ? null : row.get(key).toString();
    }
}
