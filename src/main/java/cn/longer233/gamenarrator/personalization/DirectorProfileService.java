package cn.longer233.gamenarrator.personalization;

import cn.longer233.gamenarrator.script.ScriptSegment;
import cn.longer233.gamenarrator.script.StoryboardSegmentView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DirectorProfileService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public DirectorProfileService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void recordScriptEdit(UUID taskId, ScriptSegment ai, ScriptSegment result) {
        if (ai.equals(result)) return;
        double durationRatio = ratio(result.endSeconds() - result.startSeconds(), ai.endSeconds() - ai.startSeconds());
        double densityRatio = ratio(density(result), density(ai));
        insert(taskId, ai.clipIndex(), "SCRIPT", ai, result, durationRatio, densityRatio, effect(result.effectCue()));
    }

    public void recordStoryboardEdit(UUID taskId, StoryboardSegmentView ai, StoryboardSegmentView result) {
        double durationRatio = ratio(result.endSeconds() - result.startSeconds(), ai.endSeconds() - ai.startSeconds());
        double densityRatio = ratio(density(result.narration(), result.endSeconds() - result.startSeconds()),
                density(ai.narration(), ai.endSeconds() - ai.startSeconds()));
        insert(taskId, ai.clipIndex(), "STORYBOARD", ai, result, durationRatio, densityRatio, effect(result.effectCue()));
    }

    public DirectorProfileView profile() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM director_edit_decisions", Integer.class);
        int decisions = count == null ? 0 : count;
        Double duration = jdbc.queryForObject("SELECT AVG(duration_ratio) FROM director_edit_decisions WHERE duration_ratio IS NOT NULL", Double.class);
        Double density = jdbc.queryForObject("SELECT AVG(text_density_ratio) FROM director_edit_decisions WHERE text_density_ratio IS NOT NULL", Double.class);
        List<String> effects = jdbc.query("""
                SELECT effect_preference,COUNT(*) amount FROM director_edit_decisions
                WHERE effect_preference IS NOT NULL AND effect_preference<>''
                GROUP BY effect_preference ORDER BY amount DESC,effect_preference LIMIT 5
                """, (rs, row) -> rs.getString(1));
        double durationRatio = clamp(duration == null ? 1 : duration, .65, 1.45);
        double densityRatio = clamp(density == null ? 1 : density, .65, 1.45);
        String summary = decisions == 0 ? "暂无人工修改样本，使用项目默认导演策略"
                : "基于 %d 次最终修改：镜头长度倾向 %.0f%%，文案密度倾向 %.0f%%"
                .formatted(decisions, durationRatio * 100, densityRatio * 100);
        return new DirectorProfileView(decisions, durationRatio, densityRatio, List.copyOf(effects), summary);
    }

    private void insert(UUID taskId, int clipIndex, String type, Object ai, Object result,
                        double durationRatio, double densityRatio, String effect) {
        try {
            jdbc.update("""
                    INSERT INTO director_edit_decisions(id,task_id,clip_index,decision_type,ai_value_json,
                    final_value_json,duration_ratio,text_density_ratio,effect_preference,created_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), taskId, clipIndex, type, mapper.writeValueAsString(ai),
                    mapper.writeValueAsString(result), durationRatio, densityRatio, effect, OffsetDateTime.now());
        } catch (Exception exception) {
            throw new IllegalStateException("无法保存导演修改差异", exception);
        }
    }

    private double density(ScriptSegment value) {
        return density(value.narration(), value.endSeconds() - value.startSeconds());
    }
    private double density(String text, double seconds) { return (text == null ? 0 : text.length()) / Math.max(.1, seconds); }
    private double ratio(double value, double base) { return base <= .001 ? 1 : clamp(value / base, .25, 4); }
    private double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private String effect(String cue) {
        if (cue == null || cue.isBlank()) return null;
        return cue.replaceFirst("^NARRATIVE_STAGE[^;]*;\\s*", "").trim().toLowerCase(Locale.ROOT);
    }
}
