package cn.longer233.gamenarrator.importer;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class MediaDownloadJobService {
    private static final int MAX_RETAINED_JOBS = 64;
    private static final Duration COMPLETED_JOB_TTL = Duration.ofMinutes(30);
    private final YtDlpMediaImporter importer;
    private final Executor executor;
    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();

    public MediaDownloadJobService(YtDlpMediaImporter importer,
                                   @Qualifier("taskExecutor") Executor executor) {
        this.importer = importer;
        this.executor = executor;
    }

    public String start(MediaDownloadRequest request, String sessionId) {
        cleanup();
        String id = UUID.randomUUID().toString();
        Job job = new Job(sessionId);
        jobs.put(id, job);
        executor.execute(() -> {
            try {
                job.status = "RUNNING";
                job.result = importer.download(request, progress -> job.progress = progress);
                job.status = "COMPLETED";
                job.completedAt = Instant.now();
            } catch (Exception exception) {
                job.error = exception.getMessage();
                job.status = "FAILED";
                job.completedAt = Instant.now();
            }
        });
        return id;
    }

    public MediaDownloadJobView find(String id, String sessionId) {
        cleanup();
        Job job = jobs.get(id);
        if (job == null || !job.sessionId.equals(sessionId)) return null;
        return new MediaDownloadJobView(id, job.status, job.progress, job.result, job.error);
    }

    private void cleanup() {
        Instant cutoff = Instant.now().minus(COMPLETED_JOB_TTL);
        jobs.entrySet().removeIf(entry -> entry.getValue().completedAt != null
                && entry.getValue().completedAt.isBefore(cutoff));
        if (jobs.size() <= MAX_RETAINED_JOBS) return;
        jobs.entrySet().stream()
                .filter(entry -> entry.getValue().completedAt != null)
                .sorted(java.util.Comparator.comparing(entry -> entry.getValue().completedAt))
                .limit(jobs.size() - MAX_RETAINED_JOBS)
                .map(java.util.Map.Entry::getKey)
                .toList().forEach(jobs::remove);
    }

    private static final class Job {
        private final String sessionId;
        private volatile String status = "QUEUED";
        private volatile MediaDownloadProgress progress =
                new MediaDownloadProgress("0%", "0", "0", "--", "--");
        private volatile MediaDownloadResult result;
        private volatile String error;
        private volatile Instant completedAt;
        private Job(String sessionId) { this.sessionId = sessionId; }
    }
}
