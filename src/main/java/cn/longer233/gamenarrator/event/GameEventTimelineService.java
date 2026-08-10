package cn.longer233.gamenarrator.event;

import cn.longer233.gamenarrator.highlight.HighlightClip;
import cn.longer233.gamenarrator.task.application.TaskNotFoundException;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import cn.longer233.gamenarrator.vision.FrameUnderstanding;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class GameEventTimelineService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final VideoTaskRepository tasks;
    private final BossBattleKnowledgePack knowledgePack;
    private final GameKnowledgePackService knowledgePacks;

    public GameEventTimelineService(JdbcTemplate jdbc, ObjectMapper objectMapper, VideoTaskRepository tasks) {
        this(jdbc, objectMapper, tasks, null);
    }

    @Autowired
    public GameEventTimelineService(JdbcTemplate jdbc, ObjectMapper objectMapper, VideoTaskRepository tasks,
                                    GameKnowledgePackService knowledgePacks) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.tasks = tasks;
        this.knowledgePack = loadKnowledgePack(objectMapper);
        this.knowledgePacks = knowledgePacks;
    }

    @Transactional
    public List<GameEventView> rebuild(UUID taskId, Path visualAnalysisPath, Path highlightManifestPath) {
        requireTask(taskId);
        Integer manualCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM game_events WHERE task_id=? AND manually_edited=TRUE", Integer.class, taskId);
        if (manualCount != null && manualCount > 0) return query(taskId);
        jdbc.update("DELETE FROM game_events WHERE task_id=?", taskId);
        try {
            JsonNode visual = objectMapper.readTree(visualAnalysisPath.toFile());
            JsonNode highlights = objectMapper.readTree(highlightManifestPath.toFile());
            List<FrameUnderstanding> frames = objectMapper.readerForListOf(FrameUnderstanding.class)
                    .readValue(visual.path("frames"));
            List<HighlightClip> clips = objectMapper.readerForListOf(HighlightClip.class)
                    .readValue(highlights.path("clips"));
            Map<Integer, FrameUnderstanding> byIndex = new LinkedHashMap<>();
            frames.forEach(frame -> byIndex.put(frame.index(), frame));
            for (HighlightClip clip : clips) insertDetectedEvent(taskId, clip, byIndex.get(clip.sourceFrameIndex()));
            return query(taskId);
        } catch (Exception exception) {
            throw new IllegalStateException("无法建立游戏事件时间线：" + exception.getMessage(), exception);
        }
    }

    @Transactional
    public List<GameEventView> list(UUID taskId) {
        VideoTask task = requireTask(taskId);
        List<GameEventView> existing = query(taskId);
        if (!existing.isEmpty()) return existing;
        if (task.getVisualAnalysisPath() != null && task.getHighlightManifestPath() != null) {
            Path visual = Path.of(task.getVisualAnalysisPath()).toAbsolutePath().normalize();
            Path highlights = Path.of(task.getHighlightManifestPath()).toAbsolutePath().normalize();
            if (Files.isRegularFile(visual) && Files.isRegularFile(highlights)) {
                return rebuild(taskId, visual, highlights);
            }
        }
        return List.of();
    }

    private List<GameEventView> query(UUID taskId) {
        return jdbc.query("""
                SELECT id,task_id,start_seconds,end_seconds,anchor_seconds,event_type,confidence,
                       highlight_score,description,evidence_json,confirmation_status,manually_edited,
                       knowledge_pack_code,updated_at
                FROM game_events WHERE task_id=? ORDER BY start_seconds,id
                """, (rs, rowNum) -> map(rs), taskId);
    }

    @Transactional
    public GameEventView update(UUID taskId, UUID eventId, UpdateGameEventRequest request) {
        requireTask(taskId);
        int changed = jdbc.update("""
                UPDATE game_events SET event_type=?,description=?,highlight_score=?,confirmation_status=?,
                    manually_edited=TRUE,updated_at=? WHERE id=? AND task_id=?
                """, request.eventType().trim(), request.description().trim(), request.importance(),
                request.confirmationStatus(), OffsetDateTime.now(), eventId, taskId);
        if (changed == 0) throw new IllegalArgumentException("游戏事件不存在");
        return jdbc.query("""
                SELECT id,task_id,start_seconds,end_seconds,anchor_seconds,event_type,confidence,
                       highlight_score,description,evidence_json,confirmation_status,manually_edited,
                       knowledge_pack_code,updated_at FROM game_events WHERE id=? AND task_id=?
                """, (rs, rowNum) -> map(rs), eventId, taskId).getFirst();
    }

    @Transactional
    public List<GameEventFact> confirmedFacts(UUID taskId) {
        return list(taskId).stream()
                .filter(event -> "CONFIRMED".equals(event.confirmationStatus()))
                .map(event -> new GameEventFact(event.eventType(), event.description(), event.startSeconds(),
                        event.endSeconds(), event.importance()))
                .toList();
    }

    public BossBattleKnowledgePack knowledgePack() {
        return knowledgePack;
    }

    private void insertDetectedEvent(UUID taskId, HighlightClip clip, FrameUnderstanding frame) throws Exception {
        String description = firstNonBlank(frame == null ? null : frame.description(), clip.description(), "待确认的游戏片段");
        String ocr = frame == null ? "" : firstNonBlank(frame.ocrText(), "");
        String combined = (description + " " + ocr + " " + clip.eventType()).toLowerCase(Locale.ROOT);
        List<BossBattleKnowledgePack> available = knowledgePacks == null ? List.of(knowledgePack) : knowledgePacks.activePacks();
        BossBattleKnowledgePack matchedPack = available.stream().filter(pack -> pack.eventRules().stream()
                .anyMatch(candidate -> candidate.keywords().stream().map(keyword -> keyword.toLowerCase(Locale.ROOT))
                        .anyMatch(combined::contains))).findFirst().orElse(knowledgePack);
        BossBattleKnowledgePack.EventRule rule = matchedPack.eventRules().stream()
                .filter(candidate -> candidate.keywords().stream()
                        .map(keyword -> keyword.toLowerCase(Locale.ROOT)).anyMatch(combined::contains))
                .findFirst().orElse(new BossBattleKnowledgePack.EventRule(
                        "GAME_EVENT", "一般游戏事件", List.of(), .55, Math.max(30, clip.finalScore())));
        double confidence = Math.min(.98, rule.baseConfidence() + Math.max(0, clip.finalScore() - 70) / 500.0);
        List<GameEventEvidence> evidence = new ArrayList<>();
        evidence.add(new GameEventEvidence("FRAME_DESCRIPTION", description, clip.anchorSeconds(), clip.sourceFrameIndex()));
        if (!ocr.isBlank()) evidence.add(new GameEventEvidence("OCR", ocr, clip.anchorSeconds(), clip.sourceFrameIndex()));
        evidence.add(new GameEventEvidence("HIGHLIGHT_SCORE", "多模态高光评分 " + clip.finalScore() + "/100",
                clip.anchorSeconds(), clip.sourceFrameIndex()));
        jdbc.update("""
                INSERT INTO game_events(id,task_id,start_seconds,end_seconds,event_type,confidence,
                    highlight_score,description,source_frame_index,anchor_seconds,evidence_json,
                    confirmation_status,manually_edited,knowledge_pack_code,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), taskId, clip.startSeconds(), clip.endSeconds(), rule.code(), confidence,
                Math.max(rule.importance(), clip.finalScore()), description, clip.sourceFrameIndex(),
                clip.anchorSeconds(), objectMapper.writeValueAsString(evidence), "AI_SUGGESTED", false,
                matchedPack.code(), OffsetDateTime.now());
    }

    private GameEventView map(ResultSet rs) throws SQLException {
        try {
            List<GameEventEvidence> evidence = objectMapper.readValue(rs.getString("evidence_json"),
                    new TypeReference<>() {});
            Double anchor = (Double) rs.getObject("anchor_seconds");
            return new GameEventView((UUID) rs.getObject("id"), (UUID) rs.getObject("task_id"),
                    rs.getDouble("start_seconds"), rs.getDouble("end_seconds"),
                    anchor == null ? rs.getDouble("start_seconds") : anchor,
                    rs.getString("event_type"), rs.getDouble("confidence"),
                    (int) Math.round(rs.getDouble("highlight_score")), rs.getString("description"),
                    List.copyOf(evidence), rs.getString("confirmation_status"), rs.getBoolean("manually_edited"),
                    rs.getString("knowledge_pack_code"), rs.getObject("updated_at", OffsetDateTime.class));
        } catch (Exception exception) {
            throw new SQLException("无法读取事件证据", exception);
        }
    }

    private VideoTask requireTask(UUID taskId) {
        return tasks.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    }

    private static BossBattleKnowledgePack loadKnowledgePack(ObjectMapper mapper) {
        try (InputStream input = new ClassPathResource("knowledge-packs/boss-battle-v1.json").getInputStream()) {
            return mapper.readValue(input, BossBattleKnowledgePack.class);
        } catch (Exception exception) {
            throw new IllegalStateException("无法加载 Boss 战知识包", exception);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }
}
