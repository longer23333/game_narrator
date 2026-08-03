package cn.longer233.gamenarrator.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

/** Records pipeline files in the V2 artifact model and advances the project manifest revision. */
@Component
public class ProjectArtifactRegistry {
    private static final UUID LOCAL_USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ProjectArtifactRegistry(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void record(UUID projectId, String type, String value, String mimeType, boolean temporary) {
        if (value == null || value.isBlank()) return;
        try {
            Path path = Path.of(value).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) return;
            String storageKey = path.toString();
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM artifact WHERE storage_key=? AND deleted_at IS NULL",
                    Boolean.class, storageKey))) return;
            UUID parent = jdbc.queryForObject("SELECT current_revision_id FROM video_project WHERE id=?", UUID.class, projectId);
            UUID run = jdbc.queryForObject("SELECT latest_run_id FROM video_project WHERE id=?", UUID.class, projectId);
            ObjectNode manifest = (ObjectNode) mapper.readTree(jdbc.queryForObject(
                    "SELECT manifest_json FROM project_revision WHERE id=?", String.class, parent));
            UUID artifactId = UUID.randomUUID();
            int revisionNo = jdbc.queryForObject("SELECT COALESCE(MAX(revision_no),0)+1 FROM project_revision WHERE project_id=?",
                    Integer.class, projectId);
            UUID revision = UUID.randomUUID();
            ObjectNode artifacts = manifest.with("artifacts");
            ObjectNode entry = artifacts.putObject(type);
            entry.put("artifactId", artifactId.toString());
            entry.put("storageKey", storageKey);
            entry.put("mimeType", mimeType);
            String json = mapper.writeValueAsString(manifest);
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            jdbc.update("""
                    INSERT INTO project_revision(id,project_id,revision_no,parent_revision_id,created_by,change_type,
                    change_summary,parameter_snapshot_json,manifest_json,manifest_schema_version,manifest_sha256,created_at)
                    VALUES(?,?,?,?,?,'PIPELINE_OUTPUT',?,'{}',?,?,?,?)
                    """, revision, projectId, revisionNo, parent, LOCAL_USER, "记录阶段产物：" + type,
                    json, 3, sha256(json.getBytes()), now);
            jdbc.update("""
                    INSERT INTO artifact(id,owner_id,project_id,revision_id,generation_run_id,artifact_type,storage_key,
                    mime_type,size_bytes,sha256,schema_version,temporary,expires_at,created_at,deleted_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,NULL,?,NULL)
                    """, artifactId, LOCAL_USER, projectId, revision, run, type, storageKey, mimeType,
                    Files.size(path), sha256(Files.readAllBytes(path)), 1, temporary, now);
            jdbc.update("UPDATE video_project SET current_revision_id=?,updated_at=?,version=version+1 WHERE id=?",
                    revision, now, projectId);
        } catch (Exception exception) {
            throw new IllegalStateException("阶段产物登记失败：" + type + "：" + exception.getMessage(), exception);
        }
    }

    private String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
