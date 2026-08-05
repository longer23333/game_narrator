package cn.longer233.gamenarrator.editor;

import cn.longer233.gamenarrator.task.application.TaskNotFoundException;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import jakarta.transaction.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectRevisionService {
    private final JdbcTemplate jdbc;
    private final VideoTaskRepository tasks;
    private final EditorTimelineService editor;

    public ProjectRevisionService(JdbcTemplate jdbc, VideoTaskRepository tasks, EditorTimelineService editor) {
        this.jdbc = jdbc; this.tasks = tasks; this.editor = editor;
    }

    public List<ProjectRevisionView> list(UUID taskId) {
        requireTask(taskId);
        UUID current = jdbc.queryForObject("SELECT current_revision_id FROM video_project WHERE id=?", UUID.class, taskId);
        return jdbc.query("""
                SELECT r.id,r.revision_no,r.parent_revision_id,r.revision_label,r.change_type,
                       r.change_summary,r.created_at,
                       (SELECT COUNT(*) FROM project_revision c WHERE c.parent_revision_id=r.id) child_count
                FROM project_revision r WHERE r.project_id=? ORDER BY r.revision_no
                """, (rs, row) -> new ProjectRevisionView(rs.getObject("id", UUID.class), rs.getInt("revision_no"),
                rs.getObject("parent_revision_id", UUID.class), rs.getString("revision_label"),
                rs.getString("change_type"), rs.getString("change_summary"),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("id", UUID.class).equals(current), rs.getInt("child_count")), taskId);
    }

    @Transactional
    public ProjectRevisionView rename(UUID taskId, UUID revisionId, RenameRevisionRequest request) {
        requireRevision(taskId, revisionId);
        jdbc.update("UPDATE project_revision SET revision_label=? WHERE id=? AND project_id=?",
                request.label().trim(), revisionId, taskId);
        return list(taskId).stream().filter(item -> item.id().equals(revisionId)).findFirst().orElseThrow();
    }

    @Transactional
    public com.fasterxml.jackson.databind.JsonNode checkout(UUID taskId, UUID revisionId) {
        requireRevision(taskId, revisionId);
        return editor.checkout(taskId, revisionId);
    }

    private void requireTask(UUID id) {
        if (tasks.findById(id).isEmpty()) throw new TaskNotFoundException(id);
    }
    private void requireRevision(UUID taskId, UUID revisionId) {
        requireTask(taskId);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM project_revision WHERE id=? AND project_id=?",
                Integer.class, revisionId, taskId);
        if (count == null || count == 0) throw new IllegalArgumentException("工程版本不存在");
    }
}
