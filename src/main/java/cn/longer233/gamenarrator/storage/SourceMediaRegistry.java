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
import java.util.UUID;

@Service
public class SourceMediaRegistry implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final Path storageRoot;
    public SourceMediaRegistry(JdbcTemplate jdbc) { this(jdbc, null); }
    @Autowired
    public SourceMediaRegistry(JdbcTemplate jdbc, @Value("${game-narrator.storage-root}") String storageRoot) {
        this.jdbc = jdbc;
        this.storageRoot = storageRoot == null ? null : Path.of(storageRoot).toAbsolutePath().normalize();
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
        try {
            FileStore store = Files.getFileStore(path);
            jdbc.update("""
                    MERGE INTO source_media_storage(task_id,storage_mode,storage_path,original_filename,size_bytes,
                    content_type,filesystem_key,registered_at,last_verified_at) KEY(task_id) VALUES(?,?,?,?,?,?,?,?,?)
                    """, taskId, "MANAGED", path.toAbsolutePath().normalize().toString(), originalFilename,
                    Files.size(path), contentType, store.name() + ":" + store.type(), OffsetDateTime.now(), OffsetDateTime.now());
        } catch (Exception exception) {
            throw new IllegalStateException("无法登记源视频存储信息", exception);
        }
    }
}
