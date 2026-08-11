package cn.longer233.gamenarrator.pipeline;

import cn.longer233.gamenarrator.identity.CurrentUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.transaction.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import java.io.InputStream;
import cn.longer233.gamenarrator.cloud.CloudSyncService;

/** Records pipeline files in the V2 artifact model and advances the project manifest revision. */
@Component
public class ProjectArtifactRegistry {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentUserContext currentUser;
    private final CloudSyncService cloudSync;

    public ProjectArtifactRegistry(JdbcTemplate jdbc, ObjectMapper mapper, CurrentUserContext currentUser,
                                   CloudSyncService cloudSync) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.cloudSync = cloudSync;
    }

    @Transactional
    public void record(UUID projectId, String type, String value, String mimeType, boolean temporary) {
        if (value == null || value.isBlank()) return;
        try {
            Path path = Path.of(value).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) return;
            String storageKey = path.toString();
            String contentHash = fileSha256(path);
            var existing = jdbc.query("SELECT id,sha256 FROM artifact WHERE storage_key=? AND deleted_at IS NULL",
                    (rs, row) -> new ExistingArtifact(rs.getObject("id", UUID.class), rs.getString("sha256")), storageKey);
            if (!existing.isEmpty() && contentHash.equals(existing.getFirst().sha256())) return;
            ProjectState project = jdbc.queryForObject("""
                    SELECT current_revision_id,latest_run_id,version FROM video_project WHERE id=?
                    """, (rs, row) -> new ProjectState(rs.getObject("current_revision_id", UUID.class),
                    rs.getObject("latest_run_id", UUID.class), rs.getLong("version")), projectId);
            if (project == null) throw new IllegalStateException("工程不存在：" + projectId);
            UUID parent = project.currentRevisionId();
            UUID run = project.latestRunId();
            ObjectNode manifest = (ObjectNode) mapper.readTree(jdbc.queryForObject(
                    "SELECT manifest_json FROM project_revision WHERE id=?", String.class, parent));
            UUID artifactId = existing.isEmpty() ? UUID.randomUUID() : existing.getFirst().id();
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
                    """, revision, projectId, revisionNo, parent, currentUser.userId(), "记录阶段产物：" + type,
                    json, 3, sha256(json.getBytes()), now);
            if (existing.isEmpty()) {
                jdbc.update("""
                        INSERT INTO artifact(id,owner_id,project_id,revision_id,generation_run_id,artifact_type,storage_key,
                        mime_type,size_bytes,sha256,schema_version,temporary,expires_at,created_at,deleted_at)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?,?,NULL,?,NULL)
                        """, artifactId, currentUser.userId(), projectId, revision, run, type, storageKey, mimeType,
                        Files.size(path), contentHash, 1, temporary, now);
            } else {
                jdbc.update("""
                        UPDATE artifact SET revision_id=?,generation_run_id=?,artifact_type=?,mime_type=?,size_bytes=?,
                        sha256=?,schema_version=1,temporary=?,created_at=? WHERE id=?
                        """, revision, run, type, mimeType, Files.size(path), contentHash, temporary, now, artifactId);
            }
            int changed = jdbc.update("""
                    UPDATE video_project SET current_revision_id=?,updated_at=?,version=version+1
                    WHERE id=? AND version=?
                    """, revision, now, projectId, project.version());
            if (changed != 1) {
                throw new OptimisticLockingFailureException("工程已在产物登记期间被其他操作更新：" + projectId);
            }
            cloudSync.enqueue(currentUser.userId(), "ARTIFACT", artifactId, path, mimeType,
                    contentHash, Files.size(path));
        } catch (Exception exception) {
            throw new IllegalStateException("阶段产物登记失败：" + type + "：" + exception.getMessage(), exception);
        }
    }

    private String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private String fileSha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[1024 * 1024];
        try (InputStream input = Files.newInputStream(path)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private record ExistingArtifact(UUID id, String sha256) { }
    private record ProjectState(UUID currentRevisionId, UUID latestRunId, long version) { }
}
