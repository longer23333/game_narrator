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
import cn.longer233.gamenarrator.ai.AdaptiveAiChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CreativeVariantService {
    /** 方案来源：内置写死模板 */
    public static final String ORIGIN_TEMPLATE = "TEMPLATE";
    /** 方案来源：AI 按用户需求生成 */
    public static final String ORIGIN_AI = "AI";
    private static final List<String> VARIANT_TYPES = List.of("STORY", "GUIDE", "COMEDY", "REVIEW");
    private static final Logger log = LoggerFactory.getLogger(CreativeVariantService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final VideoTaskRepository tasks;
    private final VideoTaskEngine engine;
    private final SourceMediaRegistry sourceMedia;
    private final ProjectHistoryService history;
    private final CurrentUserContext currentUser;
    private final AdaptiveAiChatClient assistant;

    public CreativeVariantService(JdbcTemplate jdbc, ObjectMapper mapper, VideoTaskRepository tasks,
                                  CurrentUserContext currentUser) {
        this(jdbc, mapper, tasks, null, null, null, currentUser, null);
    }

    @Autowired
    public CreativeVariantService(JdbcTemplate jdbc, ObjectMapper mapper, VideoTaskRepository tasks,
                                  VideoTaskEngine engine, SourceMediaRegistry sourceMedia, ProjectHistoryService history,
                                  CurrentUserContext currentUser, AdaptiveAiChatClient assistant) {
        this.jdbc = jdbc; this.mapper = mapper; this.tasks = tasks;
        this.engine = engine; this.sourceMedia = sourceMedia; this.history = history;
        this.currentUser = currentUser; this.assistant = assistant;
    }

    public CreativeVariantService(JdbcTemplate jdbc, ObjectMapper mapper, VideoTaskRepository tasks,
                                  VideoTaskEngine engine, SourceMediaRegistry sourceMedia, ProjectHistoryService history,
                                  CurrentUserContext currentUser) {
        this(jdbc, mapper, tasks, engine, sourceMedia, history, currentUser, null);
    }

    /**
     * 生成四套内置预设方案。这些是写死的剪辑模板，不经过任何 AI 推理，
     * 结果里会带 origin=TEMPLATE，界面必须据此标注，不能让用户误以为是 AI 成果。
     */
    @Transactional
    public List<CreativeVariantView> generate(UUID taskId) {
        VideoTask task = ownedTask(taskId);
        upsert(task, "STORY", "剧情版", 0.82, 0.78, "五幕结构、角色动机、危机与反转", "情绪递进，高潮前保留呼吸空间");
        upsert(task, "GUIDE", "攻略版", 0.68, 0.42, "操作步骤、机制解释、失败原因和可复现建议", "关键操作慢放，字幕信息密度较高");
        upsert(task, "COMEDY", "搞笑版", 1.18, 0.70, "反差、失误、吐槽和回收梗", "快切、定格、局部放大与短音效");
        upsert(task, "REVIEW", "复盘版", 0.88, 0.50, "按时间线还原决策，区分事实、判断和改进点", "保留关键上下文，减少装饰性特效");
        return list(taskId);
    }

    /**
     * 按用户描述调用 AI 生成方案。与预设模板的区别：失败时不再静默降级成模板，
     * 而是把原因抛给调用方（全局异常处理会转成 409 + 可读提示）。
     */
    @Transactional
    public List<CreativeVariantView> generate(UUID taskId, String brief) {
        VideoTask task = ownedTask(taskId);
        if (brief == null || brief.isBlank()) return generate(taskId);
        if (assistant == null) {
            throw new IllegalStateException("AI 创意助手未配置：请在设置里配置可用的模型后端后重试，" +
                    "或改用“生成四版预设方案”（内置模板，不调用 AI）");
        }
        String prompt = """
                你是视频创意策划。根据用户需求，为已有游戏视频生成 4 套不同的可执行剪辑方案。
                只返回 JSON 数组，每项必须包含：variantType(STORY/GUIDE/COMEDY/REVIEW)、name、title、plotStructure(数组)、shots(数组，每项含timeRange、visual、dialogue)、soundEffects(数组)、assetRequirements(数组，每项含type、description、licenseHint)、narrativeFocus、editingRule、targetDurationSeconds。
                不得虚构视频中尚未确认的事实；没有素材信息时使用“待从源视频确认”。
                用户需求：%s
                源任务：%s，游戏分类：%s，目标时长：%d 秒
                """.formatted(brief.trim(), task.getName(), task.getGameCategory(), task.getTargetDurationSeconds());
        JsonNode plans;
        try {
            plans = assistant.chatJson(prompt, List.of(), false, Duration.ofMinutes(3));
        } catch (Exception exception) {
            log.warn("AI 创意方案生成失败 taskId={}", taskId, exception);
            throw new IllegalStateException("AI 创意方案生成失败：" + rootCause(exception) +
                    "。请重试，或改用“生成四版预设方案”", exception);
        }
        if (!plans.isArray() || plans.isEmpty()) {
            log.warn("AI 创意方案返回为空 taskId={}", taskId);
            throw new IllegalStateException("AI 没有返回可解析的方案（期望包含 STORY/GUIDE/COMEDY/REVIEW 的 JSON 数组），请重试或改写需求描述");
        }
        Set<String> written = new java.util.LinkedHashSet<>();
        for (JsonNode plan : plans) {
            String type = plan.path("variantType").asText("").toUpperCase(java.util.Locale.ROOT);
            if (!VARIANT_TYPES.contains(type) || written.contains(type)) continue;
            upsertStrategy(task, type, plan.path("name").asText(type), plan, ORIGIN_AI, brief.trim());
            written.add(type);
            if (written.size() == 4) break;
        }
        if (written.isEmpty()) {
            throw new IllegalStateException("AI 返回的方案缺少 variantType(STORY/GUIDE/COMEDY/REVIEW) 字段，无法采用，请重试");
        }
        // AI 没给全的类型用预设模板补齐，但保留 TEMPLATE 标记，界面上能看出哪些是模板兜底
        for (String missing : VARIANT_TYPES) {
            if (!written.contains(missing)) upsertTemplate(task, missing, "AI 未提供该版本，已用内置模板补齐");
        }
        return list(taskId);
    }

    private static String rootCause(Throwable exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message.trim();
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
        upsert(task, type, suffix, pace, music, narrativeFocus, editingRule, null);
    }

    private void upsert(VideoTask task, String type, String suffix, double pace, double music,
                        String narrativeFocus, String editingRule, String originNote) {
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
            strategy.put("origin", ORIGIN_TEMPLATE);
            strategy.put("originLabel", "内置预设模板");
            strategy.put("originNote", originNote == null || originNote.isBlank()
                    ? "内置剪辑模板，未调用 AI；节奏与叙事焦点为固定参数" : originNote);
            strategy.put("generatedAt", OffsetDateTime.now().toString());
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

    /** 按类型补齐一套内置模板，note 会写进 strategy.originNote 供界面显示。 */
    private void upsertTemplate(VideoTask task, String type, String note) {
        switch (type) {
            case "STORY" -> upsert(task, "STORY", "剧情版", 0.82, 0.78,
                    "五幕结构、角色动机、危机与反转", "情绪递进，高潮前保留呼吸空间", note);
            case "GUIDE" -> upsert(task, "GUIDE", "攻略版", 0.68, 0.42,
                    "操作步骤、机制解释、失败原因和可复现建议", "关键操作慢放，字幕信息密度较高", note);
            case "COMEDY" -> upsert(task, "COMEDY", "搞笑版", 1.18, 0.70,
                    "反差、失误、吐槽和回收梗", "快切、定格、局部放大与短音效", note);
            default -> upsert(task, "REVIEW", "复盘版", 0.88, 0.50,
                    "按时间线还原决策，区分事实、判断和改进点", "保留关键上下文，减少装饰性特效", note);
        }
    }

    private void upsertStrategy(VideoTask task, String type, String name, JsonNode plan, String origin, String brief) {
        try {
            Map<String, Object> strategy = mapper.convertValue(plan, Map.class);
            strategy.put("sourceReuse", true);
            strategy.putIfAbsent("sourceVideoPath", task.getSourceVideoPath());
            strategy.putIfAbsent("targetDurationSeconds", targetDuration(task, type));
            strategy.putIfAbsent("narrativeFocus", "按用户需求组织已确认事件");
            strategy.putIfAbsent("editingRule", "事实优先，未确认内容标记为待确认");
            strategy.putIfAbsent("factPolicy", "已确认事件作为事实；AI 建议必须标注，不能补造战局结果");
            strategy.put("origin", origin);
            strategy.put("originLabel", ORIGIN_AI.equals(origin) ? "AI 生成" : "内置预设模板");
            strategy.put("originNote", ORIGIN_AI.equals(origin)
                    ? "由 AI 根据你的需求生成，时间点和素材建议需核对后再用" : "内置剪辑模板，未调用 AI");
            strategy.put("generatedAt", OffsetDateTime.now().toString());
            if (brief != null && !brief.isBlank()) strategy.put("brief", brief);
            OffsetDateTime now = OffsetDateTime.now();
            UUID id = jdbc.query("SELECT id FROM creative_variant WHERE source_task_id=? AND variant_type=?",
                    rs -> rs.next() ? rs.getObject(1, UUID.class) : UUID.randomUUID(), task.getId(), type);
            cn.longer233.gamenarrator.common.PortableUpsert.update(jdbc, """
                    MERGE INTO creative_variant(id,source_task_id,variant_type,name,strategy_json,status,created_at,updated_at)
                    KEY(source_task_id,variant_type) VALUES(?,?,?,?,?,'PLANNED',
                    COALESCE((SELECT created_at FROM creative_variant WHERE source_task_id=? AND variant_type=?),?),?)
                    """, "source_task_id,variant_type", id, task.getId(), type, name,
                    mapper.writeValueAsString(strategy), task.getId(), type, now, now);
        } catch (Exception exception) { throw new IllegalStateException("无法保存 AI 创意方案", exception); }
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
