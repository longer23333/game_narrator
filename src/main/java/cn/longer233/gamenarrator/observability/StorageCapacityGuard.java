package cn.longer233.gamenarrator.observability;

import cn.longer233.gamenarrator.common.StorageCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class StorageCapacityGuard {
    private final Path root;
    private final long minimumFreeBytes;
    private final int minimumFreePercent;
    private final double workingSpaceMultiplier;
    private final StorageCleanupService cleanup;

    public StorageCapacityGuard(String root, long minimumFreeBytes, int minimumFreePercent,
                                StorageCleanupService cleanup) {
        this(root, minimumFreeBytes, minimumFreePercent, 1.5, cleanup);
    }

    @Autowired
    public StorageCapacityGuard(@Value("${game-narrator.storage-root}") String root,
            @Value("${game-narrator.capacity.minimum-free-bytes:5368709120}") long minimumFreeBytes,
            @Value("${game-narrator.capacity.minimum-free-percent:5}") int minimumFreePercent,
            @Value("${game-narrator.capacity.working-space-multiplier:1.5}") double workingSpaceMultiplier,
            StorageCleanupService cleanup) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.minimumFreeBytes = Math.max(0, minimumFreeBytes);
        this.minimumFreePercent = Math.max(0, Math.min(50, minimumFreePercent));
        this.workingSpaceMultiplier = Math.max(1.0, Math.min(4.0, workingSpaceMultiplier));
        this.cleanup = cleanup;
    }

    public void requireTaskCapacity(long incomingBytes) { requireCapacity(incomingBytes); }
    public void requireUploadCapacity(long incomingBytes) {
        requireCapacity(Math.round(Math.max(0, incomingBytes) * workingSpaceMultiplier));
    }
    public void requireWorkingCapacityAfterUpload(long sourceBytes) {
        requireCapacity(Math.round(Math.max(0, sourceBytes) * (workingSpaceMultiplier - 1.0)));
    }

    public boolean acceptingTasks() { return hasCapacity(0); }
    public long usableBytes() { return root.toFile().getUsableSpace(); }
    public long totalBytes() { return root.toFile().getTotalSpace(); }
    public long reservedBytes() {
        return Math.max(minimumFreeBytes, Math.round(totalBytes() * minimumFreePercent / 100.0));
    }

    private void requireCapacity(long requiredBytes) {
        if (hasCapacity(requiredBytes)) return;
        cleanup.cleanup();
        if (hasCapacity(requiredBytes)) return;
        throw new InsufficientStorageException("磁盘可用空间不足，已暂停接收新任务；请清理存储目录或调整容量阈值");
    }

    private boolean hasCapacity(long requiredBytes) {
        try { Files.createDirectories(root); }
        catch (Exception exception) { return false; }
        return usableBytes() - Math.max(0, requiredBytes) >= reservedBytes();
    }
}
