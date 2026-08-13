package cn.longer233.gamenarrator.render;

import cn.longer233.gamenarrator.common.SecurePathGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;

@Component
public class RenderWorkLifecycle {
    private static final Logger log = LoggerFactory.getLogger(RenderWorkLifecycle.class);
    private final Path storageRoot;
    private final int attempts;
    private final long retryDelayMs;

    public RenderWorkLifecycle(@Value("${game-narrator.storage-root}") String storageRoot,
            @Value("${game-narrator.cleanup.render-work-attempts:4}") int attempts,
            @Value("${game-narrator.cleanup.render-work-retry-ms:150}") long retryDelayMs) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.attempts = Math.max(1, attempts);
        this.retryDelayMs = Math.max(0, retryDelayMs);
    }

    public boolean completed(Path taskDirectory) {
        Path work = resolveOwnedWork(taskDirectory);
        Exception last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                deleteTree(work);
                log.info("RENDER_WORK_CLEANUP_SUCCESS path={} attempt={}", work, attempt);
                return true;
            } catch (Exception exception) {
                last = exception;
                if (attempt < attempts) pause();
            }
        }
        log.warn("RENDER_WORK_CLEANUP_DEFERRED path={} attempts={} reason={}", work, attempts, last.getMessage());
        return false;
    }

    public void failed(Path taskDirectory, Throwable failure) {
        Path work = resolveOwnedWork(taskDirectory);
        if (Files.isDirectory(work, LinkOption.NOFOLLOW_LINKS)) {
            log.warn("RENDER_WORK_RETAINED_FOR_DIAGNOSIS path={} reason={}", work,
                    failure == null ? "unknown" : failure.getMessage());
        }
    }

    Path resolveOwnedWork(Path taskDirectory) {
        Path root;
        try { root = SecurePathGuard.prepareRoot(storageRoot); }
        catch (java.io.IOException exception) {
            throw new IllegalStateException("无法准备受管存储目录", exception);
        }
        Path task = taskDirectory.toAbsolutePath().normalize();
        Path work = task.resolve("render-work").normalize();
        if (!SecurePathGuard.isOwned(task, root) || !work.getParent().equals(task)
                || !"render-work".equals(work.getFileName().toString())) {
            throw new IllegalArgumentException("render-work 路径不属于受管存储目录");
        }
        return work;
    }

    void deleteTree(Path work) throws Exception {
        if (!Files.exists(work, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(work)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private void pause() {
        try { Thread.sleep(retryDelayMs); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("render-work 清理重试被中断", exception);
        }
    }
}
