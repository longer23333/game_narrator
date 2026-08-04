package cn.longer233.gamenarrator.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/** Mirrors the legacy task state into the versioned run model during the transition period. */
@Component
public class PipelineRunTracker {
    private static final UUID LOCAL_USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PipelineRunTracker(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void running(UUID taskId, String stageType) {
        UUID runId = activeRun(taskId);
        int attempt = jdbc.queryForObject(
                "SELECT COALESCE(MAX(attempt_no),0)+1 FROM stage_run WHERE generation_run_id=? AND stage_type=?",
                Integer.class, runId, stageType);
        jdbc.update("""
                INSERT INTO stage_run(id,generation_run_id,stage_type,attempt_no,status,progress,
                input_snapshot_json,started_at) VALUES(?,?,?,?,'RUNNING',10,?,?)
                """, UUID.randomUUID(), runId, stageType, attempt, json(Map.of("taskId", taskId)), now());
    }

    public void completed(UUID taskId, String stageType, Map<String, ?> summary) {
        updateStage(taskId, stageType, "COMPLETED", 100, json(summary), null);
        if ("RENDERING".equals(stageType)) finishRun(taskId, "COMPLETED", null);
    }

    public void progress(UUID taskId, String stageType, int progress) {
        UUID runId = activeRun(taskId);
        jdbc.update("""
                UPDATE stage_run SET progress=? WHERE id=(SELECT id FROM stage_run
                WHERE generation_run_id=? AND stage_type=? ORDER BY attempt_no DESC LIMIT 1)
                """, Math.max(10, Math.min(99, progress)), runId, stageType);
    }

    public void failed(UUID taskId, String stageType, String reason) {
        updateStage(taskId, stageType, "FAILED", 100, null, reason);
        finishRun(taskId, "FAILED", reason);
    }

    public void waiting(UUID taskId, String stageType, String reason) {
        updateStage(taskId, stageType, "WAITING", 10, null, reason);
        jdbc.update("UPDATE generation_run SET status='WAITING',failure_message=? WHERE id=?",
                limited(reason), activeRun(taskId));
    }

    public void cancelled(UUID taskId, String stageType, String reason) {
        updateStage(taskId, stageType, "CANCELLED", 0, null, reason);
        finishRun(taskId, "CANCELLED", reason);
    }

    private UUID activeRun(UUID taskId) {
        var runs = jdbc.query("""
                SELECT id FROM generation_run WHERE project_id=? AND status IN ('RUNNING','WAITING')
                ORDER BY created_at DESC LIMIT 1
                """, (rs, row) -> rs.getObject(1, UUID.class), taskId);
        if (!runs.isEmpty()) {
            UUID runId = runs.getFirst();
            jdbc.update("UPDATE generation_run SET status='RUNNING',failure_message=NULL WHERE id=?", runId);
            return runId;
        }
        UUID revisionId = jdbc.queryForObject(
                "SELECT current_revision_id FROM video_project WHERE id=?", UUID.class, taskId);
        UUID runId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO generation_run(id,project_id,user_id,input_revision_id,run_type,status,
                trigger_source,started_at,trace_id,created_at) VALUES(?,?,?,?,?,'RUNNING','SYSTEM',?,?,?)
                """, runId, taskId, LOCAL_USER, revisionId, "FULL_PIPELINE", now(), "task-" + taskId, now());
        jdbc.update("UPDATE video_project SET latest_run_id=?,status='PROCESSING',updated_at=? WHERE id=?",
                runId, now(), taskId);
        return runId;
    }

    private void updateStage(UUID taskId, String stageType, String status, int progress,
                             String summary, String error) {
        UUID runId = activeRun(taskId);
        int changed = jdbc.update("""
                UPDATE stage_run SET status=?,progress=?,output_summary_json=?,finished_at=?,
                elapsed_ms=DATEDIFF('MILLISECOND',started_at,?),error_message=?
                WHERE id=(SELECT id FROM stage_run WHERE generation_run_id=? AND stage_type=?
                ORDER BY attempt_no DESC LIMIT 1)
                """, status, progress, summary, now(), now(), error, runId, stageType);
        if (changed == 0) {
            running(taskId, stageType);
            updateStage(taskId, stageType, status, progress, summary, error);
        }
    }

    private void finishRun(UUID taskId, String status, String error) {
        UUID runId = activeRun(taskId);
        jdbc.update("""
                UPDATE generation_run SET status=?,finished_at=?,elapsed_ms=DATEDIFF('MILLISECOND',started_at,?),
                failure_message=? WHERE id=?
                """, status, now(), now(), error, runId);
        jdbc.update("UPDATE video_project SET status=?,updated_at=? WHERE id=?",
                "COMPLETED".equals(status) ? "READY" : status, now(), taskId);
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { return "{}"; }
    }

    private String limited(String value) {
        if (value == null) return null;
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
}
