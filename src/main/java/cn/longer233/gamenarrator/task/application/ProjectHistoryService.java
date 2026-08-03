package cn.longer233.gamenarrator.task.application;

import cn.longer233.gamenarrator.task.domain.VideoTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectHistoryService {
    static final UUID LOCAL_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ProjectHistoryService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void createInitialHistory(VideoTask task) {
        UUID projectId = task.getId();
        UUID revisionId = UUID.randomUUID();
        OffsetDateTime createdAt = task.getCreatedAt().atOffset(ZoneOffset.UTC);
        String parameters = json(Map.of(
                "name", task.getName(),
                "gameCategory", task.getGameCategory(),
                "commentaryStyle", task.getCommentaryStyle().name(),
                "targetDurationSeconds", task.getTargetDurationSeconds(),
                "taskBrief", task.getTaskBrief(),
                "sourceVideoPath", task.getSourceVideoPath()));
        Map<String, Object> manifestValues = new LinkedHashMap<>();
        manifestValues.put("schemaVersion", 2);
        manifestValues.put("projectId", projectId.toString());
        manifestValues.put("legacyTaskId", task.getId().toString());
        manifestValues.put("timelinePath", null);
        manifestValues.put("renderedVideoPath", null);
        String manifest = json(manifestValues);

        jdbc.update("""
                INSERT INTO video_project(id,owner_id,name,description,game_category,
                commentary_style,status,current_revision_id,latest_run_id,created_at,updated_at,
                deleted_at,version) VALUES(?,?,?,?,?,?,'DRAFT',NULL,NULL,?,?,NULL,0)
                """, projectId, LOCAL_USER_ID, task.getName(), task.getTaskBrief(),
                task.getGameCategory(), task.getCommentaryStyle().name(), createdAt, createdAt);
        jdbc.update("""
                INSERT INTO project_revision(id,project_id,revision_no,parent_revision_id,created_by,
                change_type,change_summary,parameter_snapshot_json,manifest_json,
                manifest_schema_version,manifest_sha256,created_at)
                VALUES(?,?,1,NULL,?,'INITIAL',?,?,?,?,?,?)
                """, revisionId, projectId, LOCAL_USER_ID, "任务创建时生成的初始工程版本",
                parameters, manifest, 2, sha256(manifest), createdAt);
        jdbc.update("UPDATE video_project SET current_revision_id=? WHERE id=?", revisionId, projectId);
        jdbc.update("UPDATE video_tasks SET owner_id=?,project_id=? WHERE id=?",
                LOCAL_USER_ID, projectId, task.getId());
    }

    public void renameProject(UUID projectId, String name) {
        jdbc.update("UPDATE video_project SET name=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",
                name, projectId);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法创建工程版本快照", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算工程清单摘要", exception);
        }
    }
}
