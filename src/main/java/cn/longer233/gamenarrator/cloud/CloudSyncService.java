package cn.longer233.gamenarrator.cloud;

import cn.longer233.gamenarrator.common.SecurePathGuard;
import cn.longer233.gamenarrator.identity.CurrentUserContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CloudSyncService {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<CloudObjectStore> objectStore;
    private final CurrentUserContext currentUser;
    private final Path storageRoot;

    public CloudSyncService(JdbcTemplate jdbc, ObjectProvider<CloudObjectStore> objectStore,
                            CurrentUserContext currentUser,
                            @Value("${game-narrator.storage-root}") String storageRoot) {
        this.jdbc = jdbc;
        this.objectStore = objectStore;
        this.currentUser = currentUser;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @Transactional
    public void enqueue(UUID userId, String itemType, UUID localId, Path localPath,
                        String contentType, String sha256, long size) {
        if (userId == null || localId == null || localPath == null) return;
        Path normalized = localPath.toAbsolutePath().normalize();
        if (!normalized.startsWith(storageRoot)) return;
        String objectKey = userId + "/" + itemType.toLowerCase() + "/" + localId + "/" + normalized.getFileName();
        int changed = jdbc.update("""
                UPDATE cloud_sync_item SET local_path=?,object_key=?,content_sha256=?,size_bytes=?,sync_status='PENDING',
                last_error=NULL,updated_at=?,next_attempt_at=NULL WHERE user_id=? AND item_type=? AND local_id=?
                """, normalized.toString(), objectKey, sha256, size, now(), userId, itemType, localId);
        if (changed == 0) jdbc.update("""
                INSERT INTO cloud_sync_item(id,user_id,item_type,local_id,local_path,object_key,content_sha256,size_bytes,
                sync_status,updated_at,attempt_count) VALUES(?,?,?,?,?,?,?,?,'PENDING',?,0)
                """, UUID.randomUUID(), userId, itemType, localId, normalized.toString(), objectKey, sha256, size, now());
    }

    public List<Map<String,Object>> mine() {
        return jdbc.queryForList("""
                SELECT id,item_type,local_id,object_key,size_bytes,sync_status,last_error,updated_at,synced_at
                FROM cloud_sync_item WHERE user_id=? ORDER BY updated_at DESC
                """, currentUser.userId());
    }

    @Scheduled(fixedDelayString = "${game-narrator.cloud-sync.interval-ms:5000}")
    public void uploadPending() {
        CloudObjectStore store = objectStore.getIfAvailable();
        if (store == null) return;
        var rows = jdbc.query("""
                SELECT id,local_path,object_key,content_sha256 FROM cloud_sync_item
                WHERE sync_status IN ('PENDING','FAILED') AND (next_attempt_at IS NULL OR next_attempt_at<=?)
                ORDER BY updated_at LIMIT 5
                """, (rs, index) -> new PendingItem(rs.getObject("id", UUID.class), rs.getString("local_path"),
                rs.getString("object_key"), rs.getString("content_sha256")), now());
        for (var row : rows) upload(store, row);
    }

    public Path restore(UUID localId, String itemType, Path target) {
        CloudObjectStore store = objectStore.getIfAvailable();
        if (store == null) throw new IllegalStateException("Cloud object storage is not enabled");
        var rows = jdbc.query("""
                SELECT object_key,content_sha256 FROM cloud_sync_item
                WHERE user_id=? AND local_id=? AND item_type=? AND sync_status='SYNCED'
                """, (rs, index) -> new RemoteObject(rs.getString(1), rs.getString(2)),
                currentUser.userId(), localId, itemType);
        if (rows.isEmpty()) throw new IllegalStateException("No synced object is available for this project");
        Path normalized = target.toAbsolutePath().normalize();
        if (!SecurePathGuard.isOwned(normalized, storageRoot)) throw new IllegalArgumentException("Invalid restore path");
        try {
            store.download(rows.getFirst().objectKey(), normalized, rows.getFirst().sha256());
            return normalized;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to restore cloud object: " + exception.getMessage(), exception);
        }
    }

    @Transactional
    public Path restoreSource(UUID taskId, Path target) {
        Path restored = restore(taskId, "SOURCE_MEDIA", target);
        jdbc.update("UPDATE video_tasks SET source_video_path=? WHERE id=? AND owner_id=?",
                restored.toString(), taskId, currentUser.userId());
        jdbc.update("UPDATE source_media_storage SET storage_path=?,last_verified_at=? WHERE task_id=?",
                restored.toString(), now(), taskId);
        return restored;
    }

    public Path restoreLatestArtifact(UUID projectId, String artifactType, Path target) {
        CloudObjectStore store = objectStore.getIfAvailable();
        if (store == null) throw new IllegalStateException("Cloud object storage is not enabled");
        var rows = jdbc.query("""
                SELECT s.object_key,s.content_sha256 FROM cloud_sync_item s JOIN artifact a ON a.id=s.local_id
                WHERE s.user_id=? AND a.project_id=? AND a.artifact_type=? AND s.sync_status='SYNCED'
                ORDER BY s.synced_at DESC LIMIT 1
                """, (rs, index) -> new RemoteObject(rs.getString(1), rs.getString(2)),
                currentUser.userId(), projectId, artifactType);
        if (rows.isEmpty()) throw new IllegalStateException("No synced project artifact is available");
        Path normalized = target.toAbsolutePath().normalize();
        if (!SecurePathGuard.isOwned(normalized, storageRoot)) throw new IllegalArgumentException("Invalid restore path");
        try { store.download(rows.getFirst().objectKey(), normalized, rows.getFirst().sha256()); return normalized; }
        catch (Exception exception) { throw new IllegalStateException("Unable to restore cloud object: " + exception.getMessage(), exception); }
    }

    private void upload(CloudObjectStore store, PendingItem row) {
        UUID id = row.id();
        try {
            Path source = Path.of(row.localPath()).toAbsolutePath().normalize();
            if (!source.startsWith(storageRoot) || !Files.isRegularFile(source)) throw new IllegalStateException("Local file is unavailable");
            store.upload(row.objectKey(), source, Files.probeContentType(source), row.sha256());
            jdbc.update("UPDATE cloud_sync_item SET sync_status='SYNCED',last_error=NULL,synced_at=?,updated_at=? WHERE id=?",
                    now(), now(), id);
        } catch (Exception failure) {
            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            jdbc.update("""
                    UPDATE cloud_sync_item SET sync_status='FAILED',attempt_count=attempt_count+1,last_error=?,
                    next_attempt_at=?,updated_at=? WHERE id=?
                    """, message.substring(0, Math.min(1000, message.length())), now().plusMinutes(5), now(), id);
        }
    }

    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
    private record PendingItem(UUID id, String localPath, String objectKey, String sha256) {}
    private record RemoteObject(String objectKey, String sha256) {}
}
