package cn.longer233.gamenarrator.community;

import cn.longer233.gamenarrator.task.application.TaskNotFoundException;
import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import cn.longer233.gamenarrator.pipeline.VideoTaskEngine;
import cn.longer233.gamenarrator.storage.SourceMediaRegistry;
import cn.longer233.gamenarrator.task.application.ProjectHistoryService;
import cn.longer233.gamenarrator.task.domain.CommentaryStyle;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CreativeVariantService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final VideoTaskRepository tasks;
    private final VideoTaskEngine engine;
    private final SourceMediaRegistry sourceMedia;
    private final ProjectHistoryService history;
    private final CurrentUserContext currentUser;

    public CreativeVariantService(JdbcTemplate jdbc, ObjectMapper mapper, VideoTaskRepository tasks,
                                  CurrentUserContext currentUser) {
        this(jdbc, mapper, tasks, null, null, null, currentUser);
    }

    @Autowired
    public CreativeVariantService(JdbcTemplate jdbc, ObjectMapper mapper, VideoTaskRepository tasks,
                                  VideoTaskEngine engine, SourceMediaRegistry sourceMedia, ProjectHistoryService history,
                                  CurrentUserContext currentUser) {
        this.jdbc = jdbc; this.mapper = mapper; this.tasks = tasks;
        this.engine = engine; this.sourceMedia = sourceMedia; this.history = history;
        this.currentUser = currentUser;
    }

    @Transactional
    public List<CreativeVariantView> generate(UUID taskId) {
        VideoTask task = ownedTask(taskId);
        upsert(task, "STORY", "剧情版", 0.82, 0.78, "五幕结构、角色动机、危机与反转", "情绪递进，高潮前保留呼吸空间");
        upsert(task, "GUIDE", "攻略版", 0.68, 0.42, "操作步骤、机制解释、失败原因和可复现建议", "关键操作慢放，字幕信息密度较高");
        upsert(task, "COMEDY", "搞笑版", 1.18, 0.70, "反差、失误、吐槽和回收梗", "快切、定格、局部放大与短音效");
        upsert(task, "REVIEW", "复盘版", 0.88, 0.50, "按时间线还原决策，区分事实、判断和改进点", "保留关键上下文，减少装饰性特效");
        return list(taskId);
    }

    public List<CreativeVariantView> list(UUID taskId) {
        ownedTask(taskId);
        return listOwned(taskId);
    }

    private List<CreativeVariantView> listOwned(UUID taskId) {
        return jdbc.query("""
                SELECT id,source_task_id,variant_type,name,strategy_json,status,generated_task_id,created_at,updated_at
                FROM creative_variant WHERE source_task_id=? ORDER BY CASE variant_type
                WHEN 'STORY' THEN 1 WHEN 'GUIDE' THEN 2 WHEN 'COMEDY' THEN 3 ELSE 4 END
                """, (rs, row) -> {
            try {
                return new CreativeVariantView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4), mapper.readTree(rs.getString(5)), rs.getString(6),
                        rs.getObject(7, UUID.class), rs.getObject(8, OffsetDateTime.class), rs.getObject(9, OffsetDateTime.class));
            } catch (Exception exception) { throw new IllegalStateException("版本策略内容损坏", exception); }
        }, taskId);
    }

    @Transactional
    public CreativeVariantView materialize(UUID taskId, UUID variantId) {
        if (engine == null || sourceMedia == null || history == null) throw new IllegalStateException("版本生成依赖未配置");
        VideoTask source = ownedTask(taskId);
        Map<String, Object> row = jdbc.query("""
                SELECT v.source_task_id,v.variant_type,v.name,v.strategy_json,v.generated_task_id
                FROM creative_variant v WHERE v.id=? AND v.source_task_id=?
                """, (rs, index) -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("SOURCE_TASK_ID", rs.getObject(1, UUID.class));
            value.put("VARIANT_TYPE", rs.getString(2));
            value.put("NAME", rs.getString(3));
            value.put("STRATEGY_JSON", rs.getString(4));
            value.put("GENERATED_TASK_ID", rs.getObject(5, UUID.class));
            return value;
        }, variantId, taskId).stream().findFirst().orElseThrow(() -> new TaskNotFoundException(taskId));
        if (row.get("GENERATED_TASK_ID") != null) {
            return listOwned(taskId).stream().filter(item -> item.id().equals(variantId)).findFirst().orElseThrow();
        }
        try {
            JsonNode strategy = mapper.readTree(row.get("STRATEGY_JSON").toString());
            String type = row.get("VARIANT_TYPE").toString();
            CommentaryStyle style = "COMEDY".equals(type) ? CommentaryStyle.HUMOROUS :
                    "STORY".equals(type) ? CommentaryStyle.ANIME_THEATER : CommentaryStyle.PASSIONATE;
            String brief = strategy.path("narrativeFocus").asText() + "；" + strategy.path("editingRule").asText();
            VideoTask generated = new VideoTask(row.get("NAME").toString(), source.getGameCategory(), style,
                    strategy.path("targetDurationSeconds").asInt(source.getTargetDurationSeconds()), brief,
                    source.getSourceVideoPath(), true);
            generated.configureEditingScope(source.getEditingScope());
            generated.configureTerminologyGlossary(source.getTerminologyGlossary());
            generated.configureAiOptions(true, source.isCloudVisionEnabled(), source.isAiScriptEnabled(),
                    source.isAiVoiceEnabled(), source.isAutoAssetsEnabled());
            generated.assignOwnership(currentUser.userId());
            generated = tasks.saveAndFlush(generated);
            history.createInitialHistory(generated);
            sourceMedia.registerReferenced(generated.getId(), java.nio.file.Path.of(source.getSourceVideoPath()),
                    "共享源录像：" + source.getName(), null);
            jdbc.update("UPDATE creative_variant SET generated_task_id=?,status='APPLIED',updated_at=? WHERE id=?",
                    generated.getId(), OffsetDateTime.now(), variantId);
            UUID generatedId = generated.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { engine.start(generatedId); }
            });
            return listOwned(taskId).stream().filter(item -> item.id().equals(variantId)).findFirst().orElseThrow();
        } catch (RuntimeException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("无法创建独立成片任务", exception); }
    }

    private void upsert(VideoTask task, String type, String suffix, double pace, double music,
                        String narrativeFocus, String editingRule) {
        try {
            Map<String, Object> strategy = new LinkedHashMap<>();
            strategy.put("sourceReuse", true);
            strategy.put("sourceVideoPath", task.getSourceVideoPath());
            strategy.put("targetDurationSeconds", targetDuration(task, type));
            strategy.put("paceMultiplier", pace);
            strategy.put("musicIntensity", music);
            strategy.put("narrativeFocus", narrativeFocus);
            strategy.put("editingRule", editingRule);
            strategy.put("factPolicy", "已确认事件作为事实；AI 建议必须标注，不能补造战局结果");
            OffsetDateTime now = OffsetDateTime.now();
            UUID id = jdbc.query("SELECT id FROM creative_variant WHERE source_task_id=? AND variant_type=?",
                    rs -> rs.next() ? rs.getObject(1, UUID.class) : UUID.randomUUID(), task.getId(), type);
            cn.longer233.gamenarrator.common.PortableUpsert.update(jdbc, """
                    MERGE INTO creative_variant(id,source_task_id,variant_type,name,strategy_json,status,created_at,updated_at)
                    KEY(source_task_id,variant_type) VALUES(?,?,?,?,?,'PLANNED',
                    COALESCE((SELECT created_at FROM creative_variant WHERE source_task_id=? AND variant_type=?),?),?)
                    """, "source_task_id,variant_type", id, task.getId(), type, task.getName() + " · " + suffix,
                    mapper.writeValueAsString(strategy), task.getId(), type, now, now);
        } catch (Exception exception) { throw new IllegalStateException("无法生成创作版本策略", exception); }
    }

    private int targetDuration(VideoTask task, String type) {
        double ratio = switch (type) { case "STORY" -> 1.15; case "GUIDE" -> 1.25; case "COMEDY" -> .75; default -> 1.35; };
        return Math.max(15, (int) Math.round(task.getTargetDurationSeconds() * ratio));
    }

    private VideoTask ownedTask(UUID taskId) {
        return tasks.findByIdAndOwnerId(taskId, currentUser.userId())
                .orElseThrow(() -> new TaskNotFoundException(taskId));
    }
}
