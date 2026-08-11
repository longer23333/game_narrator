package cn.longer233.gamenarrator.storage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.security.MessageDigest;
import java.io.InputStream;
import java.util.HexFormat;
import java.util.UUID;
import cn.longer233.gamenarrator.cloud.CloudSyncService;

@Service
public class SourceMediaRegistry implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final Path storageRoot;
    private final CloudSyncService cloudSync;
    public SourceMediaRegistry(JdbcTemplate jdbc) { this(jdbc, null, null); }
    @Autowired
    public SourceMediaRegistry(JdbcTemplate jdbc, @Value("${game-narrator.storage-root}") String storageRoot,
                               CloudSyncService cloudSync) {
        this.jdbc = jdbc;
        this.storageRoot = storageRoot == null ? null : Path.of(storageRoot).toAbsolutePath().normalize();
        this.cloudSync = cloudSync;
    }

    @Override public void run(ApplicationArguments args) {
        if (storageRoot == null) return;
        jdbc.query("""
                SELECT t.id,t.source_video_path FROM video_tasks t
                LEFT JOIN source_media_storage s ON s.task_id=t.id WHERE s.task_id IS NULL
                """, rs -> {
            Path path = Path.of(rs.getString(2)).toAbsolutePath().normalize();
            if (path.startsWith(storageRoot) && Files.isRegularFile(path)) {
                registerManaged((UUID) rs.getObject(1), path, path.getFileName().toString(), null);
            }
        });
    }

    public void registerManaged(UUID taskId, Path path, String originalFilename, String contentType) {
        register(taskId, path, originalFilename, contentType, "MANAGED");
    }

    public void registerReferenced(UUID taskId, Path path, String originalFilename, String contentType) {
        register(taskId, path, originalFilename, contentType, "REFERENCED");
    }

    private void register(UUID taskId, Path path, String originalFilename, String contentType, String mode) {
        try {
            FileStore store = Files.getFileStore(path);
            OffsetDateTime timestamp = OffsetDateTime.now();
            int changed = jdbc.update("""
                    UPDATE source_media_storage SET storage_mode=?,storage_path=?,original_filename=?,size_bytes=?,
                    content_type=?,filesystem_key=?,last_verified_at=? WHERE task_id=?
                    """, mode, path.toAbsolutePath().normalize().toString(), originalFilename, Files.size(path),
                    contentType, store.name() + ":" + store.type(), timestamp, taskId);
            if (changed == 0) jdbc.update("""
                    INSERT INTO source_media_storage(task_id,storage_mode,storage_path,original_filename,size_bytes,
                    content_type,filesystem_key,registered_at,last_verified_at) VALUES(?,?,?,?,?,?,?,?,?)
                    """, taskId, mode, path.toAbsolutePath().normalize().toString(), originalFilename,
                    Files.size(path), contentType, store.name() + ":" + store.type(), timestamp, timestamp);
            if (cloudSync != null && "MANAGED".equals(mode)) {
                UUID ownerId = jdbc.queryForObject("SELECT owner_id FROM video_tasks WHERE id=?", UUID.class, taskId);
                cloudSync.enqueue(ownerId, "SOURCE_MEDIA", taskId, path, contentType, sha256(path), Files.size(path));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("无法登记源视频存储信息", exception);
        }
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024 * 1024]; int read;
            while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
