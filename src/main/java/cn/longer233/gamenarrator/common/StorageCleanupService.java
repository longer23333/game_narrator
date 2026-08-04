package cn.longer233.gamenarrator.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collection;

@Component
@Order(30)
public class StorageCleanupService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StorageCleanupService.class);
    private final Path storageRoot;
    private final Duration retention;
    private final JdbcTemplate jdbc;

    public StorageCleanupService(@Value("${game-narrator.storage-root}") String storageRoot,
                                 @Value("${game-narrator.cleanup.retention-hours:24}") long retentionHours,
                                 JdbcTemplate jdbc) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.retention = Duration.ofHours(Math.max(1, retentionHours));
        this.jdbc = jdbc;
    }

    @Override public void run(ApplicationArguments args) { cleanup(); }

    @Scheduled(fixedDelayString = "${game-narrator.cleanup.interval-ms:3600000}")
    public void cleanup() {
        try {
            Path root = SecurePathGuard.prepareRoot(storageRoot);
            Instant cutoff = Instant.now().minus(retention);
            int temporary = cleanupTemporaryFiles(root, cutoff);
            int imports = cleanupTree(root.resolve("import-downloads"), root, cutoff);
            int exports = cleanupExpiredExports(root);
            if (temporary + imports + exports > 0) {
                log.info("STORAGE_CLEANUP temporaryFiles={} importEntries={} expiredExports={}",
                        temporary, imports, exports);
            }
        } catch (Exception exception) {
            log.warn("STORAGE_CLEANUP_FAILED reason={}", exception.getMessage());
        }
    }

    public void cleanupTask(UUID taskId, Collection<String> knownArtifacts) {
        try {
            Path root = SecurePathGuard.prepareRoot(storageRoot);
            if (knownArtifacts != null) {
                for (String value : knownArtifacts) {
                    if (value == null || value.isBlank()) continue;
                    Path artifact = Path.of(value).toAbsolutePath().normalize();
                    if ("timeline.json".equals(String.valueOf(artifact.getFileName()))) {
                        deleteOwnedTree(artifact.getParent().resolve("render-preview"), root);
                    }
                    deleteOwned(artifact, root);
                }
            }
            cleanupTaskNamedFiles(root.resolve("segment-clips"), root, taskId + "-");
            cleanupTaskNamedFiles(root.resolve("import-downloads"), root, taskId.toString());
            cleanupTaskNamedFiles(root.resolve("assets").resolve("projects"), root, taskId.toString());
            deleteOwnedTree(root.resolve("tasks").resolve(taskId.toString()), root);
            List<Map<String, Object>> projectAssets = jdbc.queryForList(
                    "SELECT id,local_path FROM external_asset WHERE provider='PROJECT' AND external_id=?",
                    taskId.toString());
            for (Map<String, Object> asset : projectAssets) {
                Object localPath = asset.get("LOCAL_PATH");
                if (localPath != null) deleteOwned(Path.of(localPath.toString()).toAbsolutePath().normalize(), root);
                jdbc.update("DELETE FROM asset_tag_override WHERE asset_id=?", asset.get("ID"));
                jdbc.update("DELETE FROM asset_tag_assignment WHERE asset_id=?", asset.get("ID"));
                jdbc.update("DELETE FROM external_asset WHERE id=?", asset.get("ID"));
            }
            log.info("TASK_STORAGE_CLEANUP taskId={} knownArtifacts={}", taskId,
                    knownArtifacts == null ? 0 : knownArtifacts.size());
        } catch (Exception exception) {
            log.warn("TASK_STORAGE_CLEANUP_FAILED taskId={} reason={}", taskId, exception.getMessage());
        }
    }

    private void cleanupTaskNamedFiles(Path directory, Path root, String prefix) throws Exception {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !SecurePathGuard.isOwned(directory, root)) return;
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(candidate -> candidate.getFileName().toString().startsWith(prefix)).toList()) {
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) deleteOwnedTree(path, root);
                else deleteOwned(path, root);
            }
        }
    }

    private void deleteOwnedTree(Path directory, Path root) throws Exception {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                || !SecurePathGuard.isOwned(directory, root)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) deleteOwned(path, root);
        }
    }

    private int cleanupTemporaryFiles(Path root, Instant cutoff) throws Exception {
        int deleted = 0;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (!path.getFileName().toString().endsWith(".tmp")) continue;
                if (olderThan(path, cutoff) && deleteOwned(path, root)) deleted++;
            }
        }
        return deleted;
    }

    private int cleanupTree(Path directory, Path root, Instant cutoff) throws Exception {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !SecurePathGuard.isOwned(directory, root)) return 0;
        int deleted = 0;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (path.equals(directory)) continue;
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    try (var children = Files.list(path)) {
                        if (children.findAny().isEmpty() && deleteOwned(path, root)) deleted++;
                    }
                } else if (olderThan(path, cutoff) && deleteOwned(path, root)) deleted++;
            }
        }
        return deleted;
    }

    private int cleanupExpiredExports(Path root) {
        List<Map<String, Object>> expired = jdbc.queryForList("""
                SELECT ej.id job_id,a.id artifact_id,a.storage_key
                FROM export_job ej JOIN artifact a ON a.id=ej.output_artifact_id
                WHERE ej.expires_at<? AND ej.status='COMPLETED' AND a.deleted_at IS NULL
                """, OffsetDateTime.now());
        int cleaned = 0;
        for (Map<String, Object> row : expired) {
            Path path = Path.of(row.get("STORAGE_KEY").toString()).toAbsolutePath().normalize();
            if (!SecurePathGuard.isOwned(path, root)) {
                log.warn("EXPORT_CLEANUP_SKIPPED reason=outside_storage_root jobId={}", row.get("JOB_ID"));
                continue;
            }
            try {
                Files.deleteIfExists(path);
                OffsetDateTime now = OffsetDateTime.now();
                jdbc.update("UPDATE artifact SET deleted_at=? WHERE id=?", now, row.get("ARTIFACT_ID"));
                jdbc.update("UPDATE export_job SET status='EXPIRED' WHERE id=?", row.get("JOB_ID"));
                cleaned++;
            } catch (Exception exception) {
                log.warn("EXPORT_CLEANUP_FAILED jobId={} reason={}", row.get("JOB_ID"), exception.getMessage());
            }
        }
        return cleaned;
    }

    private boolean olderThan(Path path, Instant cutoff) throws Exception {
        return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff);
    }

    private boolean deleteOwned(Path path, Path root) throws Exception {
        if (!SecurePathGuard.isOwned(path, root)) return false;
        return Files.deleteIfExists(path);
    }
}
