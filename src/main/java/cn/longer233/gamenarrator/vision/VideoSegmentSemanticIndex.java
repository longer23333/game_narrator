package cn.longer233.gamenarrator.vision;

import cn.longer233.gamenarrator.asset.BgeAssetSemanticSearch;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import javax.imageio.ImageIO;

@Service
public class VideoSegmentSemanticIndex {
    private static final Logger log = LoggerFactory.getLogger(VideoSegmentSemanticIndex.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final BgeAssetSemanticSearch embeddings;
    private final Path storageRoot;

    public VideoSegmentSemanticIndex(JdbcTemplate jdbc, ObjectMapper objectMapper,
            BgeAssetSemanticSearch embeddings,
            @Value("${game-narrator.storage-root}") String storageRoot) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.embeddings = embeddings;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public void index(UUID taskId, Path visualAnalysisPath) {
        if (!embeddings.available()) return;
        try {
            var root = objectMapper.readTree(visualAnalysisPath.toFile());
            List<FrameUnderstanding> frames = objectMapper.readerForListOf(FrameUnderstanding.class)
                    .readValue(root.path("frames"));
            List<String> texts = frames.stream().map(this::semanticText).toList();
            List<double[]> vectors = embeddings.embedTexts(texts);
            if (vectors.size() != frames.size()) throw new IllegalStateException("BGE 返回的镜头向量数量不一致");
            for (int index = 0; index < frames.size(); index++) {
                FrameUnderstanding frame = frames.get(index);
                String text = texts.get(index);
                jdbc.update("""
                        MERGE INTO video_segment_embedding(task_id,frame_index,timestamp_seconds,event_type,
                        description,image_path,image_hash,model,content_hash,vector_json,indexed_at) KEY(task_id,frame_index)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?)
                        """, taskId, frame.index(), frame.timestampSeconds(), frame.eventType(), frame.description(),
                        frame.imagePath(), imageHash(Path.of(frame.imagePath())), embeddings.model(), sha256(text),
                        objectMapper.writeValueAsString(vectors.get(index)), OffsetDateTime.now());
            }
            log.info("VIDEO_SEGMENT_INDEXED taskId={} frames={} model={}", taskId, frames.size(), embeddings.model());
        } catch (Exception exception) {
            log.warn("VIDEO_SEGMENT_INDEX_SKIPPED taskId={} reason={}", taskId, exception.getMessage());
        }
    }

    public List<VideoSegmentSearchResult> search(String query, int requestedLimit) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("请输入镜头搜索内容");
        int limit = Math.max(1, Math.min(30, requestedLimit));
        try {
            backfillMissingTasks();
            String normalizedQuery = query.trim();
            double[] queryVector = embeddings.embedTexts(List.of(expandSearchQuery(normalizedQuery))).getFirst();
            List<Scored> scored = jdbc.query("""
                    SELECT e.task_id,t.name,e.frame_index,e.timestamp_seconds,e.event_type,
                    e.description,e.vector_json FROM video_segment_embedding e
                    JOIN video_tasks t ON t.id=e.task_id WHERE e.model=?
                    """, (rs, row) -> {
                double[] vector = parseVector(rs.getString("vector_json"));
                double semantic = embeddings.similarity(queryVector, vector);
                String eventType = rs.getString("event_type");
                String description = rs.getString("description");
                String taskName = rs.getString("name");
                double hybrid = hybridScore(normalizedQuery, semantic, taskName, eventType, description);
                return new Scored(new VideoSegmentSearchResult(
                        rs.getObject("task_id", UUID.class), rs.getString("name"), rs.getInt("frame_index"),
                        rs.getDouble("timestamp_seconds"), eventType, description, hybrid));
            }, embeddings.model());
            List<VideoSegmentSearchResult> ordered = scored.stream()
                    .sorted(Comparator.comparingDouble((Scored item) -> item.result.similarity()).reversed())
                    .map(Scored::result).toList();
            return diversify(ordered, limit);
        } catch (Exception exception) {
            throw new IllegalStateException("本地镜头语义搜索失败：" + exception.getMessage(), exception);
        }
    }

    public List<VideoSegmentSearchResult> searchByImage(byte[] imageBytes, int requestedLimit) {
        if (imageBytes == null || imageBytes.length == 0) throw new IllegalArgumentException("请选择截图");
        if (imageBytes.length > 10 * 1024 * 1024) throw new IllegalArgumentException("截图不能超过 10 MB");
        try {
            var image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) throw new IllegalArgumentException("无法识别该图片格式");
            if ((long) image.getWidth() * image.getHeight() > 25_000_000L) {
                throw new IllegalArgumentException("截图分辨率过大");
            }
            backfillMissingTasks();
            backfillImageHashes();
            long queryHash = ImagePerceptualHash.differenceHash(image);
            int limit = Math.max(1, Math.min(30, requestedLimit));
            return jdbc.query("""
                    SELECT e.task_id,t.name,e.frame_index,e.timestamp_seconds,e.event_type,
                    e.description,e.image_hash FROM video_segment_embedding e
                    JOIN video_tasks t ON t.id=e.task_id WHERE e.image_hash IS NOT NULL
                    """, (rs, row) -> new VideoSegmentSearchResult(
                    rs.getObject("task_id", UUID.class), rs.getString("name"), rs.getInt("frame_index"),
                    rs.getDouble("timestamp_seconds"), rs.getString("event_type"), rs.getString("description"),
                    ImagePerceptualHash.similarity(queryHash, rs.getLong("image_hash"))))
                    .stream().sorted(Comparator.comparingDouble(VideoSegmentSearchResult::similarity).reversed())
                    .limit(limit).toList();
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("截图镜头搜索失败：" + exception.getMessage(), exception);
        }
    }

    private void backfillMissingTasks() {
        List<TaskAnalysis> missing = jdbc.query("""
                SELECT t.id,t.visual_analysis_path FROM video_tasks t
                WHERE t.visual_analysis_path IS NOT NULL
                AND NOT EXISTS (SELECT 1 FROM video_segment_embedding e WHERE e.task_id=t.id AND e.model=?)
                """, (rs, row) -> new TaskAnalysis(rs.getObject(1, UUID.class), rs.getString(2)), embeddings.model());
        for (TaskAnalysis task : missing) {
            try {
                Path analysis = Path.of(task.path()).toAbsolutePath().normalize();
                if (analysis.startsWith(storageRoot) && Files.isRegularFile(analysis)) index(task.id(), analysis);
            } catch (Exception exception) {
                log.warn("VIDEO_SEGMENT_BACKFILL_SKIPPED taskId={} reason={}", task.id(), exception.getMessage());
            }
        }
    }

    private void backfillImageHashes() {
        List<FrameImage> missing = jdbc.query("""
                SELECT task_id,frame_index,image_path FROM video_segment_embedding WHERE image_hash IS NULL
                """, (rs, row) -> new FrameImage(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3)));
        for (FrameImage frame : missing) {
            try {
                Long hash = imageHash(Path.of(frame.path()));
                if (hash != null) jdbc.update("UPDATE video_segment_embedding SET image_hash=? WHERE task_id=? AND frame_index=?",
                        hash, frame.taskId(), frame.frameIndex());
            } catch (Exception exception) {
                log.debug("VIDEO_SEGMENT_IMAGE_HASH_SKIPPED taskId={} frame={} reason={}",
                        frame.taskId(), frame.frameIndex(), exception.getMessage());
            }
        }
    }

    private Long imageHash(Path source) throws Exception {
        Path path = source.toAbsolutePath().normalize();
        if (!path.startsWith(storageRoot) || !Files.isRegularFile(path)) return null;
        var image = ImageIO.read(path.toFile());
        return image == null ? null : ImagePerceptualHash.differenceHash(image);
    }

    public Path thumbnail(UUID taskId, int frameIndex) {
        List<String> paths = jdbc.query("SELECT image_path FROM video_segment_embedding WHERE task_id=? AND frame_index=?",
                (rs, row) -> rs.getString(1), taskId, frameIndex);
        if (paths.isEmpty()) throw new IllegalArgumentException("镜头不存在");
        Path path = Path.of(paths.getFirst()).toAbsolutePath().normalize();
        if (!path.startsWith(storageRoot) || !Files.isRegularFile(path)) {
            throw new IllegalStateException("镜头缩略图不存在或不属于存储目录");
        }
        return path;
    }

    private String semanticText(FrameUnderstanding frame) {
        return "%s；事件：%s；时间：%.2f秒".formatted(frame.description(), frame.eventType(), frame.timestampSeconds());
    }

    static double hybridScore(String query, double semantic, String taskName, String eventType, String description) {
        String searchable = String.join(" ", Objects.toString(taskName, ""), Objects.toString(eventType, ""),
                Objects.toString(description, "")).toLowerCase(Locale.ROOT);
        List<String> terms = searchTerms(query);
        long hits = terms.stream().filter(searchable::contains).count();
        double lexical = terms.isEmpty() ? 0 : (double) hits / terms.size();
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        double phrase = normalized.length() >= 2 && searchable.contains(normalized) ? 1 : 0;
        double event = eventIntentScore(normalized, Objects.toString(eventType, "").toLowerCase(Locale.ROOT));
        return Math.max(0, Math.min(1, semantic * .68 + lexical * .20 + event * .08 + phrase * .04));
    }

    static List<VideoSegmentSearchResult> diversify(List<VideoSegmentSearchResult> ordered, int limit) {
        List<VideoSegmentSearchResult> selected = new ArrayList<>();
        for (VideoSegmentSearchResult candidate : ordered) {
            boolean nearDuplicate = selected.stream().anyMatch(item -> item.taskId().equals(candidate.taskId())
                    && Math.abs(item.timestampSeconds() - candidate.timestampSeconds()) < 3.0);
            if (!nearDuplicate) selected.add(candidate);
            if (selected.size() == limit) return selected;
        }
        if (selected.size() < limit) for (VideoSegmentSearchResult candidate : ordered) {
            if (!selected.contains(candidate)) selected.add(candidate);
            if (selected.size() == limit) break;
        }
        return List.copyOf(selected);
    }

    private static String expandSearchQuery(String query) {
        StringBuilder result = new StringBuilder(query);
        Map.of("战斗", " fight combat action", "胜利", " victory win", "失败", " defeat game over",
                "搞笑", " funny comedy", "对话", " dialogue conversation", "风景", " landscape scenery",
                "爆炸", " explosion blast", "追逐", " chase pursuit").forEach((key, value) -> {
            if (query.contains(key)) result.append(value);
        });
        return result.toString();
    }

    private static List<String> searchTerms(String query) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String normalized = query.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]+", " ").trim();
        for (String part : normalized.split(" ")) if (part.length() >= 2) terms.add(part);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[\\p{IsHan}]{2,}").matcher(normalized);
        while (matcher.find()) {
            String chinese = matcher.group(); terms.add(chinese);
            if (chinese.length() > 2) for (int i = 0; i < chinese.length() - 1; i++) terms.add(chinese.substring(i, i + 2));
        }
        return List.copyOf(terms);
    }

    private static double eventIntentScore(String query, String eventType) {
        if ((query.matches(".*(战斗|攻击|boss|打斗).*")) && eventType.matches(".*(battle|combat|action|fight|boss).*")) return 1;
        if ((query.matches(".*(胜利|成功|通关).*")) && eventType.matches(".*(victory|win|success|clear).*")) return 1;
        if ((query.matches(".*(失败|死亡|翻车).*")) && eventType.matches(".*(fail|death|lose|game.over).*")) return 1;
        if ((query.matches(".*(对话|剧情|交谈).*")) && eventType.matches(".*(dialog|story|conversation).*")) return 1;
        return 0;
    }
    private double[] parseVector(String value) {
        try { return objectMapper.readValue(value, double[].class); }
        catch (Exception exception) { throw new IllegalStateException("镜头向量缓存损坏", exception); }
    }
    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    private record Scored(VideoSegmentSearchResult result) {}
    private record TaskAnalysis(UUID id, String path) {}
    private record FrameImage(UUID taskId, int frameIndex, String path) {}
}
