package cn.longer233.gamenarrator.pipeline;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Resolves the latest live pipeline artifact without exposing storage columns on VideoTask. */
@Component
public class TaskArtifactLocator {
    private final JdbcTemplate jdbc;

    public TaskArtifactLocator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Path> latest(UUID taskId, String artifactType) {
        return jdbc.query("""
                SELECT a.storage_key FROM artifact a
                JOIN video_tasks t ON t.project_id=a.project_id
                WHERE t.id=? AND a.artifact_type=? AND a.deleted_at IS NULL
                ORDER BY a.created_at DESC
                """, (rs, row) -> Path.of(rs.getString(1)).toAbsolutePath().normalize(), taskId, artifactType)
                .stream().findFirst();
    }

    public Map<String, Path> latestAll(UUID taskId) {
        Map<String, Path> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT a.artifact_type,a.storage_key FROM artifact a
                JOIN video_tasks t ON t.project_id=a.project_id
                WHERE t.id=? AND a.deleted_at IS NULL
                ORDER BY a.created_at DESC
                """, rs -> {
            String type = rs.getString(1);
            result.putIfAbsent(type, Path.of(rs.getString(2)).toAbsolutePath().normalize());
        }, taskId);
        return Map.copyOf(result);
    }
}
