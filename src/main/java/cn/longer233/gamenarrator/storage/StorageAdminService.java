package cn.longer233.gamenarrator.storage;

import cn.longer233.gamenarrator.common.StorageCleanupService;
import cn.longer233.gamenarrator.observability.StorageCapacityGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class StorageAdminService {
    private final Path root;
    private final JdbcTemplate jdbc;
    private final StorageCapacityGuard capacity;
    private final StorageCleanupService cleanup;

    public StorageAdminService(@Value("${game-narrator.storage-root}") String root, JdbcTemplate jdbc,
                               StorageCapacityGuard capacity, StorageCleanupService cleanup) {
        this.root = Path.of(root).toAbsolutePath().normalize(); this.jdbc = jdbc;
        this.capacity = capacity; this.cleanup = cleanup;
    }

    public StorageAdminView inspect() {
        Long sourceBytes = jdbc.queryForObject("SELECT COALESCE(SUM(size_bytes),0) FROM source_media_storage WHERE storage_mode='MANAGED'", Long.class);
        Long artifactBytes = jdbc.queryForObject("SELECT COALESCE(SUM(size_bytes),0) FROM artifact WHERE deleted_at IS NULL", Long.class);
        Integer sourceCount = jdbc.queryForObject("SELECT COUNT(*) FROM source_media_storage", Integer.class);
        Integer artifactCount = jdbc.queryForObject("SELECT COUNT(*) FROM artifact WHERE deleted_at IS NULL", Integer.class);
        List<StorageAdminView.LargeSource> largest = jdbc.query("""
                SELECT s.task_id,t.name,s.storage_path,s.size_bytes,s.storage_mode
                FROM source_media_storage s JOIN video_tasks t ON t.id=s.task_id
                ORDER BY s.size_bytes DESC LIMIT 20
                """, (rs, row) -> new StorageAdminView.LargeSource((java.util.UUID) rs.getObject(1), rs.getString(2),
                rs.getString(3), rs.getLong(4), rs.getString(5)));
        return new StorageAdminView(root.toString(), filesystemType(), capacity.totalBytes(), capacity.usableBytes(), capacity.reservedBytes(),
                sourceBytes == null ? 0 : sourceBytes, artifactBytes == null ? 0 : artifactBytes,
                sourceCount == null ? 0 : sourceCount, artifactCount == null ? 0 : artifactCount, List.copyOf(largest));
    }

    public StorageAdminView cleanupAndInspect() { cleanup.cleanup(); return inspect(); }
    private String filesystemType() {
        try { return java.nio.file.Files.getFileStore(root).type(); }
        catch (Exception exception) { return "UNKNOWN"; }
    }
}
