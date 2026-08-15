package cn.longer233.gamenarrator.pipeline;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;

/** Resolves the latest live pipeline artifact without exposing storage columns on VideoTask. */
@Component
public class TaskArtifactLocator {
    private static final Map<String, String> LEGACY_COLUMNS = Map.ofEntries(
            Map.entry("EXTRACTED_AUDIO", "extracted_audio_path"),
            Map.entry("SCENE_MANIFEST", "scene_manifest_path"),
            Map.entry("TRANSCRIPT_TEXT", "transcript_text_path"),
            Map.entry("TRANSCRIPT_SUBTITLE", "subtitle_path"),
            Map.entry("TRANSCRIPT_DETAIL", "transcript_json_path"),
            Map.entry("VISION_ANALYSIS", "visual_analysis_path"),
            Map.entry("HIGHLIGHT_MANIFEST", "highlight_manifest_path"),
            Map.entry("SCRIPT_MANIFEST", "generated_script_path"),
            Map.entry("VOICE_MANIFEST", "voice_manifest_path"),
            Map.entry("TIMELINE_MANIFEST", "timeline_path"),
            Map.entry("RENDERED_VIDEO", "rendered_video_path"),
            Map.entry("GENERATED_SUBTITLE", "generated_subtitle_path"));
    private final JdbcTemplate jdbc;

    public TaskArtifactLocator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Path> latest(UUID taskId, String artifactType) {
        Optional<Path> indexed = jdbc.query("""
                SELECT a.storage_key FROM artifact a
                JOIN video_tasks t ON t.project_id=a.project_id
                WHERE t.id=? AND a.artifact_type=? AND a.deleted_at IS NULL
                ORDER BY a.created_at DESC
                """, (rs, row) -> Path.of(rs.getString(1)).toAbsolutePath().normalize(), taskId, artifactType)
                .stream().findFirst();
        if (indexed.isPresent()) return indexed;
        String column = LEGACY_COLUMNS.get(artifactType);
        if (column == null) return Optional.empty();
        return jdbc.query("SELECT " + column + " FROM video_tasks WHERE id=?",
                        (rs, row) -> rs.getString(1), taskId).stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> Path.of(value).toAbsolutePath().normalize())
                .findFirst();
    }
}
