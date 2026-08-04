package cn.longer233.gamenarrator.observability;

import cn.longer233.gamenarrator.common.StorageCleanupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class StorageCapacityGuard {
    private final Path root;
    private final long minimumFreeBytes;
    private final int minimumFreePercent;
    private final StorageCleanupService cleanup;

    public StorageCapacityGuard(@Value("${game-narrator.storage-root}") String root,
            @Value("${game-narrator.capacity.minimum-free-bytes:5368709120}") long minimumFreeBytes,
            @Value("${game-narrator.capacity.minimum-free-percent:5}") int minimumFreePercent,
            StorageCleanupService cleanup) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.minimumFreeBytes = Math.max(0, minimumFreeBytes);
        this.minimumFreePercent = Math.max(0, Math.min(50, minimumFreePercent));
        this.cleanup = cleanup;
    }

    public void requireTaskCapacity(long incomingBytes) {
        if (hasCapacity(incomingBytes)) return;
        cleanup.cleanup();
        if (hasCapacity(incomingBytes)) return;
        throw new InsufficientStorageException("磁盘可用空间不足，已暂停接收新任务；请清理存储目录或调整容量阈值");
    }

    public boolean acceptingTasks() { return hasCapacity(0); }
    public long usableBytes() { return root.toFile().getUsableSpace(); }
    public long totalBytes() { return root.toFile().getTotalSpace(); }

    private boolean hasCapacity(long incomingBytes) {
        try { Files.createDirectories(root); }
        catch (Exception exception) { return false; }
        long total = totalBytes();
        long reserved = Math.max(minimumFreeBytes, Math.round(total * minimumFreePercent / 100.0));
        return usableBytes() - Math.max(0, incomingBytes) >= reserved;
    }
}
