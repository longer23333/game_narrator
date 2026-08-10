package cn.longer233.gamenarrator.community;

import cn.longer233.gamenarrator.task.application.TaskNotFoundException;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EditingDecisionReportService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final VideoTaskRepository tasks;

    public EditingDecisionReportService(JdbcTemplate jdbc, ObjectMapper mapper, VideoTaskRepository tasks) {
        this.jdbc = jdbc; this.mapper = mapper; this.tasks = tasks;
    }

    @Transactional
    public EditingDecisionReportView generate(UUID taskId) {
        VideoTask task = tasks.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
        List<Map<String, Object>> events = jdbc.queryForList("""
                SELECT start_seconds,end_seconds,event_type,description,confidence,highlight_score,
                       confirmation_status,manually_edited,knowledge_pack_code
                FROM game_events WHERE task_id=? ORDER BY start_seconds
                """, taskId);
        List<Map<String, Object>> edits = jdbc.queryForList("""
                SELECT clip_index,decision_type,duration_ratio,text_density_ratio,effect_preference,created_at
                FROM director_edit_decisions WHERE task_id=? ORDER BY created_at
                """, taskId);
        List<Map<String, Object>> variants = jdbc.queryForList("""
                SELECT variant_type,name,status FROM creative_variant
                WHERE source_task_id=? ORDER BY created_at
                """, taskId);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportVersion", 1);
        report.put("task", Map.of("id", taskId, "name", task.getName(), "gameCategory", task.getGameCategory(),
                "commentaryStyle", task.getCommentaryStyle(), "targetDurationSeconds", task.getTargetDurationSeconds()));
        report.put("sourcePolicy", Map.of("reusedByVariants", true, "databaseStoresMetadataOnly", true));
        report.put("summary", Map.of("eventCount", events.size(),
                "confirmedEventCount", events.stream().filter(item -> "CONFIRMED".equals(item.get("CONFIRMATION_STATUS"))).count(),
                "manualDecisionCount", edits.size(), "variantCount", variants.size()));
        report.put("events", events); report.put("manualEdits", edits); report.put("variants", variants);
        report.put("decisionPrinciples", List.of("优先采用已确认游戏事件", "保留高分镜头并说明排序依据",
                "用户最终修改覆盖 AI 建议", "不同版本复用同一源录像但采用独立叙事策略"));
        try {
            JsonNode reportJson = mapper.valueToTree(report);
            String markdown = markdown(task, events, edits, variants);
            UUID id = UUID.randomUUID(); OffsetDateTime now = OffsetDateTime.now();
            jdbc.update("INSERT INTO editing_decision_report(id,task_id,report_json,report_markdown,report_version,generated_at) VALUES(?,?,?,?,1,?)",
                    id, taskId, mapper.writeValueAsString(reportJson), markdown, now);
            return new EditingDecisionReportView(id, taskId, reportJson, markdown, 1, now);
        } catch (Exception exception) { throw new IllegalStateException("无法生成剪辑决策报告", exception); }
    }

    public EditingDecisionReportView latest(UUID taskId) {
        return jdbc.query("""
                SELECT id,task_id,report_json,report_markdown,report_version,generated_at
                FROM editing_decision_report WHERE task_id=? ORDER BY generated_at DESC LIMIT 1
                """, rs -> {
            if (!rs.next()) return null;
            try { return new EditingDecisionReportView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                    mapper.readTree(rs.getString(3)), rs.getString(4), rs.getInt(5),
                    rs.getObject(6, OffsetDateTime.class)); }
            catch (Exception exception) { throw new IllegalStateException("剪辑决策报告内容损坏", exception); }
        }, taskId);
    }

    private String markdown(VideoTask task, List<Map<String, Object>> events, List<Map<String, Object>> edits,
                            List<Map<String, Object>> variants) {
        long confirmed = events.stream().filter(item -> "CONFIRMED".equals(item.get("CONFIRMATION_STATUS"))).count();
        StringBuilder text = new StringBuilder("# GameNarrator 剪辑决策报告\n\n");
        text.append("- 项目：").append(task.getName()).append("\n- 游戏分类：").append(task.getGameCategory())
                .append("\n- 目标时长：").append(task.getTargetDurationSeconds()).append(" 秒\n")
                .append("- 已识别事件：").append(events.size()).append("，已确认事实：").append(confirmed).append("\n")
                .append("- 用户修改样本：").append(edits.size()).append("\n\n## 多版本方案\n\n");
        for (Map<String, Object> item : variants) text.append("- ").append(item.get("NAME")).append("：")
                .append(item.get("STATUS")).append("\n");
        text.append("\n## 决策原则\n\n1. 已确认事件优先于模型推断。\n2. AI 建议与用户最终修改分别留痕。\n")
                .append("3. 剧情、攻略、搞笑、复盘版本复用同一源录像，避免重复占用磁盘。\n")
                .append("4. 报告可用于二次修改、答辩附件和毕业设计过程说明。\n");
        return text.toString();
    }
}
