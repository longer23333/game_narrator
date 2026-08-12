package cn.longer233.gamenarrator.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.common.OwnedResourceNotFoundException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ExportService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ExportWorker worker;
    private final CurrentUserContext currentUser;

    public ExportService(JdbcTemplate jdbc, ObjectMapper objectMapper, ExportWorker worker,
                         CurrentUserContext currentUser) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.worker = worker;
        this.currentUser = currentUser;
    }

    public List<ExportPresetView> presets() {
        return jdbc.query("""
                SELECT id,name,description,container,video_codec,audio_codec,width,height,frame_rate,
                rate_control,quality_value,target_bitrate_kbps,hardware_encoder,audio_bitrate_kbps,
                audio_sample_rate,subtitle_mode FROM export_preset ORDER BY system_preset DESC,name
                """, (rs, n) -> new ExportPresetView(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("description"),
                rs.getString("container"), rs.getString("video_codec"), rs.getString("audio_codec"),
                number(rs, "width"), number(rs, "height"),
                rs.getObject("frame_rate") == null ? null : rs.getDouble("frame_rate"),
                rs.getString("rate_control"), number(rs, "quality_value"), number(rs, "target_bitrate_kbps"),
                rs.getString("hardware_encoder"), number(rs, "audio_bitrate_kbps"),
                rs.getInt("audio_sample_rate"), rs.getString("subtitle_mode")));
    }

    public ExportJobView create(UUID taskId, CreateExportRequest request) {
        Map<String, Object> task = one("""
                SELECT id,project_id,owner_id,rendered_video_path,generated_subtitle_path,
                voice_manifest_path FROM video_tasks WHERE id=? AND owner_id=?
                """, taskId, currentUser.userId());
        Path source = requiredFile(task.get("RENDERED_VIDEO_PATH"), "任务尚未生成可导出的成片");
        ExportPresetView preset = presets().stream().filter(item -> item.id().equals(request.presetId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("导出预设不存在"));
        UUID projectId = (UUID) task.get("PROJECT_ID");
        UUID ownerId = (UUID) task.get("OWNER_ID");
        UUID revisionId = jdbc.queryForObject("SELECT current_revision_id FROM video_project WHERE id=?",
                UUID.class, projectId);
        if (revisionId == null) throw new IllegalStateException("项目没有可导出的工程版本");
        UUID jobId = UUID.randomUUID();
        String safeName = request.exportName().replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (safeName.isBlank()) safeName = "game-narrator-" + taskId;
        String extension = preset.container().toLowerCase(Locale.ROOT);
        Path output = source.getParent().resolve("exports").resolve(jobId + "-" + safeName + "." + extension);
        String snapshot;
        try {
            snapshot = objectMapper.writeValueAsString(Map.of(
                    "preset", preset, "overrides", request, "schemaVersion", 1));
        } catch (Exception exception) {
            throw new IllegalStateException("无法保存导出参数", exception);
        }
        jdbc.update("""
                INSERT INTO export_job(id,project_id,revision_id,requested_by,preset_id,export_name,
                settings_snapshot_json,status,progress,output_artifact_id,started_at,completed_at,
                expires_at,downloaded_at,download_count,error_code,error_message,created_at)
                VALUES(?,?,?,?,?,? ,?,'PENDING',0,NULL,NULL,NULL,NULL,NULL,0,NULL,NULL,?)
                """, jobId, projectId, revisionId, ownerId, preset.id(), safeName, snapshot, OffsetDateTime.now());
        worker.execute(jobId, source, optionalPath(task.get("GENERATED_SUBTITLE_PATH")),
                optionalPath(task.get("VOICE_MANIFEST_PATH")), output, preset, request);
        return findJob(jobId);
    }

    public List<ExportJobView> jobs(UUID taskId) {
        return jdbc.query("""
                SELECT ej.*,ep.name preset_name,ep.container,a.storage_key,a.size_bytes
                FROM export_job ej JOIN export_preset ep ON ep.id=ej.preset_id
                LEFT JOIN artifact a ON a.id=ej.output_artifact_id
                WHERE ej.project_id=(SELECT project_id FROM video_tasks WHERE id=? AND owner_id=?)
                  AND ej.requested_by=?
                ORDER BY ej.created_at DESC
                """, (rs, n) -> map(rs), taskId, currentUser.userId(), currentUser.userId());
    }

    public ExportJobView findJob(UUID id) {
        return jdbc.query("""
                SELECT ej.*,ep.name preset_name,ep.container,a.storage_key,a.size_bytes
                FROM export_job ej JOIN export_preset ep ON ep.id=ej.preset_id
                LEFT JOIN artifact a ON a.id=ej.output_artifact_id WHERE ej.id=? AND ej.requested_by=?
                """, (rs, n) -> map(rs), id, currentUser.userId()).stream().findFirst()
                .orElseThrow(OwnedResourceNotFoundException::new);
    }

    public Download download(UUID jobId) {
        Map<String, Object> row = one("""
                SELECT ej.export_name,ep.container,a.storage_key
                FROM export_job ej JOIN export_preset ep ON ep.id=ej.preset_id
                JOIN artifact a ON a.id=ej.output_artifact_id
                WHERE ej.id=? AND ej.status='COMPLETED' AND ej.requested_by=?
                """, jobId, currentUser.userId());
        Path path = requiredFile(row.get("STORAGE_KEY"), "导出文件不存在或已过期");
        jdbc.update("UPDATE export_job SET download_count=download_count+1,downloaded_at=? WHERE id=?",
                OffsetDateTime.now(), jobId);
        return new Download(new FileSystemResource(path), row.get("EXPORT_NAME") + "." +
                row.get("CONTAINER").toString().toLowerCase(Locale.ROOT));
    }

    private ExportJobView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ExportJobView(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getObject("preset_id", UUID.class), rs.getString("preset_name"), rs.getString("export_name"),
                rs.getString("container"), rs.getString("status"), rs.getInt("progress"),
                rs.getString("storage_key") == null ? null : Path.of(rs.getString("storage_key")).getFileName().toString(),
                rs.getObject("size_bytes") == null ? null : rs.getLong("size_bytes"),
                rs.getString("error_message"), rs.getInt("download_count"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class));
    }

    private Integer number(java.sql.ResultSet rs, String name) throws java.sql.SQLException {
        return rs.getObject(name) == null ? null : rs.getInt(name);
    }

    private Map<String, Object> one(String sql, Object... arguments) {
        return jdbc.queryForList(sql, arguments).stream().findFirst()
                .orElseThrow(OwnedResourceNotFoundException::new);
    }

    private Path requiredFile(Object value, String message) {
        if (value == null) throw new IllegalStateException(message);
        Path path = Path.of(value.toString()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IllegalStateException(message);
        return path;
    }

    private Path optionalPath(Object value) {
        return value == null ? null : Path.of(value.toString()).toAbsolutePath().normalize();
    }

    public record Download(FileSystemResource resource, String filename) {}
}
