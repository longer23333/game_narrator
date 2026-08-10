package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Debounced automatic backups of the active project into app-private storage.
 * Writes are atomic (temp + rename) and retention keeps only the newest files.
 */
public final class ProjectBackupManager {
    public static final int KEEP_BACKUPS = 5;
    public static final long MIN_INTERVAL_MS = 60_000L;
    private static final String BACKUP_DIR = "project-backups";
    private static final int MAX_BACKUP_BYTES = 20 * 1024 * 1024;

    private final File backupDir;
    private final ProjectRepository repository;
    private final ExecutorService executor;
    private final AtomicBoolean pending = new AtomicBoolean();
    private volatile long lastBackupAt;

    public ProjectBackupManager(Context context, ProjectRepository repository) {
        this.backupDir = new File(context.getFilesDir(), BACKUP_DIR);
        this.repository = repository;
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void requestBackup() {
        long now = System.currentTimeMillis();
        if (now - lastBackupAt < MIN_INTERVAL_MS) return;
        if (!pending.compareAndSet(false, true)) return;
        lastBackupAt = now;
        executor.execute(() -> {
            try {
                backupNow();
            } catch (Exception ignored) {
                // Best-effort: a failed snapshot must never break editing.
            } finally {
                pending.set(false);
            }
        });
    }

    public File backupNow() throws Exception {
        if (!backupDir.isDirectory() && !backupDir.mkdirs()) {
            throw new IllegalStateException("无法创建备份目录");
        }
        String json = repository.exportActiveProject();
        long now = System.currentTimeMillis();
        File temp = new File(backupDir, "backup-" + now + ".tmp");
        File target = new File(backupDir, "backup-" + now + ".json");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
        if (!temp.renameTo(target)) {
            temp.delete();
            throw new IOException("备份写入失败");
        }
        prune();
        return target;
    }

    public long restoreLatest() throws Exception {
        List<File> backups = listBackupFiles();
        if (backups.isEmpty()) throw new IllegalStateException("没有可用备份");
        String json = new String(readLimited(backups.get(0), MAX_BACKUP_BYTES), StandardCharsets.UTF_8);
        return repository.importProject(json);
    }

    public List<File> listBackupFiles() {
        File[] files = backupDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return new ArrayList<>();
        List<File> out = new ArrayList<>(Arrays.asList(files));
        out.sort(Comparator.comparingLong(File::lastModified).reversed());
        return out;
    }

    public void close() {
        executor.shutdownNow();
    }

    private void prune() {
        List<File> backups = listBackupFiles();
        for (int i = KEEP_BACKUPS; i < backups.size(); i++) backups.get(i).delete();
    }

    private static byte[] readLimited(File file, int limit) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int total = 0, read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > limit) throw new IllegalArgumentException("备份文件超过允许大小");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
