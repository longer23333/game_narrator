package cn.longer233.gamenarrator.community;

import cn.longer233.gamenarrator.ai.AdaptiveAiChatClient;
import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.task.application.CreateVideoTaskCommand;
import cn.longer233.gamenarrator.task.application.VideoTaskService;
import cn.longer233.gamenarrator.task.domain.CommentaryStyle;
import cn.longer233.gamenarrator.task.domain.EditingScope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CreativeAssistantService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AdaptiveAiChatClient ai;
    private final VideoTaskService tasks;
    private final CurrentUserContext currentUser;

    public CreativeAssistantService(JdbcTemplate jdbc, ObjectMapper mapper, AdaptiveAiChatClient ai,
                                    VideoTaskService tasks, CurrentUserContext currentUser) {
        this.jdbc = jdbc; this.mapper = mapper; this.ai = ai; this.tasks = tasks; this.currentUser = currentUser;
    }

    public CreativeAssistantSessionView create(String brief) {
        if (brief == null || brief.isBlank() || brief.length() > 2000) throw new IllegalArgumentException("创作需求不能为空且不能超过 2000 字");
        try {
            JsonNode plans = ai.chatJson("""
                    你是视频创意助手。根据用户的模糊需求生成 4 套互相差异明显的可执行脚本方案。
                    只返回 JSON 数组。每项必须包含 type(STORY/GUIDE/COMEDY/REVIEW)、name、title、plotStructure(数组)、shots(数组，每项含timeRange、visual、dialogue)、soundEffects(数组)、assetRequirements(数组，每项含type、description、licenseHint)、taskBrief、targetDurationSeconds、commentaryStyle。
                    不得声称看过尚未提供的视频，不得编造事实；需要源视频确认的内容标记为待确认。
                    用户需求：""" + brief.trim(), List.of(), false, Duration.ofMinutes(3));
            if (!plans.isArray() || plans.isEmpty()) throw new IllegalStateException("AI 未返回有效方案");
            UUID id = UUID.randomUUID(); OffsetDateTime now = OffsetDateTime.now();
            jdbc.update("INSERT INTO creative_assistant_session(id,owner_id,brief,plans_json,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?)",
                    id, currentUser.userId(), brief.trim(), mapper.writeValueAsString(plans), "PLANNED", now, now);
            return view(id, brief.trim(), plans, "PLANNED", null);
        } catch (Exception e) { throw new IllegalStateException("无法生成创意方案：" + e.getMessage(), e); }
    }

    public CreativeAssistantSessionView get(UUID id) {
        CreativeAssistantSessionView view = jdbc.query("SELECT brief,plans_json,status,selected_type,generated_task_id FROM creative_assistant_session WHERE id=? AND owner_id=?",
                rs -> rs.next() ? read(id, rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getObject(5, UUID.class)) : null,
                id, currentUser.userId());
        if (view == null) throw new IllegalArgumentException("创意会话不存在或无权访问");
        return view;
    }

    public CreativeAssistantSessionView materialize(UUID id, String type, String name, MultipartFile video) {
        CreativeAssistantSessionView session = get(id);
        if (type == null || type.isBlank()) throw new IllegalArgumentException("必须选择创意方案");
        if (video == null || video.isEmpty()) throw new IllegalArgumentException("必须上传源视频");
        if (video.getSize() > 5L * 1024 * 1024 * 1024) throw new IllegalArgumentException("源视频不能超过 5GB");
        JsonNode plan = null;
        for (JsonNode item : session.plans()) if (item.path("type").asText("").equalsIgnoreCase(type)) { plan = item; break; }
        if (plan == null) throw new IllegalArgumentException("方案类型不存在");
        CommentaryStyle style = parseStyle(plan.path("commentaryStyle").asText("ANIME_THEATER"));
        CreateVideoTaskCommand command = new CreateVideoTaskCommand(
                name == null || name.isBlank() ? plan.path("title").asText("AI 创意视频") : name.trim(),
                "未分类", style, Math.max(15, Math.min(3600, plan.path("targetDurationSeconds").asInt(60))),
                EditingScope.FULL_VIDEO, plan.path("taskBrief").asText(session.brief()), "", false, true,
                false, true, true, true);
        try {
            var task = tasks.create(command, video);
            jdbc.update("UPDATE creative_assistant_session SET status='APPLIED',selected_type=?,generated_task_id=?,updated_at=? WHERE id=? AND owner_id=?",
                    type.toUpperCase(Locale.ROOT), task.id(), OffsetDateTime.now(), id, currentUser.userId());
            return get(id);
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("无法根据创意方案创建剪辑任务：" + e.getMessage(), e); }
    }

    public JsonNode assetRequirements(UUID id, String type) {
        CreativeAssistantSessionView session = get(id);
        if (type == null || type.isBlank()) throw new IllegalArgumentException("必须选择创意方案");
        for (JsonNode plan : session.plans()) {
            if (type.equalsIgnoreCase(plan.path("type").asText(""))) {
                JsonNode requirements = plan.path("assetRequirements");
                return requirements.isArray() ? requirements : mapper.createArrayNode();
            }
        }
        throw new IllegalArgumentException("方案类型不存在");
    }

    private CreativeAssistantSessionView read(UUID id, String brief, String json, String status, String type, UUID task) {
        try { return view(id, brief, mapper.readTree(json), status, task, type); }
        catch (Exception e) { throw new IllegalStateException("创意方案内容损坏", e); }
    }
    private CreativeAssistantSessionView view(UUID id, String brief, JsonNode plans, String status, UUID task) { return view(id, brief, plans, status, task, null); }
    private CreativeAssistantSessionView view(UUID id, String brief, JsonNode plans, String status, UUID task, String type) { return new CreativeAssistantSessionView(id, brief, plans, status, type, task); }
    private CommentaryStyle parseStyle(String value) { try { return CommentaryStyle.valueOf(value.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return CommentaryStyle.ANIME_THEATER; } }
}
