package cn.longer233.gamenarrator.pipeline;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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

    /**
     * 批量版 latestAll：任务列表一次查询取回所有任务的产物，避免每个任务一次查询的 N+1。
     * 同一 artifact_type 仍取 created_at 最新的一条（ORDER BY created_at DESC + putIfAbsent）。
     */
    public Map<UUID, Map<String, Path>> latestAllByTaskIds(Collection<UUID> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) return Map.of();
        List<UUID> ids = taskIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Map<UUID, Map<String, Path>> collected = new java.util.HashMap<>();
        jdbc.query("""
                SELECT t.id,a.artifact_type,a.storage_key FROM artifact a
                JOIN video_tasks t ON t.project_id=a.project_id
                WHERE t.id IN (%s) AND a.deleted_at IS NULL
                ORDER BY a.created_at DESC
                """.formatted(placeholders), rs -> {
            UUID taskId = rs.getObject(1, UUID.class);
            collected.computeIfAbsent(taskId, ignored -> new LinkedHashMap<>())
                    .putIfAbsent(rs.getString(2), Path.of(rs.getString(3)).toAbsolutePath().normalize());
        }, ids.toArray());
        Map<UUID, Map<String, Path>> result = new java.util.HashMap<>();
        collected.forEach((taskId, artifacts) -> result.put(taskId, Map.copyOf(artifacts)));
        return Map.copyOf(result);
    }
}
