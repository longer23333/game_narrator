package cn.longer233.gamenarrator.task.application;

import cn.longer233.gamenarrator.common.StorageCleanupService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TaskFileCleanupService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TaskFileCleanupService.class);
    private static final TypeReference<List<String>> PATH_LIST = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final StorageCleanupService cleanup;

    public TaskFileCleanupService(JdbcTemplate jdbc, ObjectMapper mapper, StorageCleanupService cleanup) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.cleanup = cleanup;
    }

    public void enqueue(UUID taskId, List<String> paths) {
        try {
            jdbc.update("INSERT INTO task_file_cleanup_job(task_id,artifact_paths,status,attempt_count,created_at) VALUES(?,?,'PENDING',0,?)",
                    taskId, mapper.writeValueAsString(paths == null ? List.of() : paths), OffsetDateTime.now());
        } catch (Exception exception) {
            throw new IllegalStateException("无法登记任务文件清理记录", exception);
        }
    }

    public void process(UUID taskId) {
        List<String> payloads = jdbc.query("SELECT artifact_paths FROM task_file_cleanup_job WHERE task_id=? AND status<>'COMPLETED'",
                (rs, row) -> rs.getString(1), taskId);
        if (payloads.isEmpty()) return;
        try {
            int claimed = jdbc.update("UPDATE task_file_cleanup_job SET status='PROCESSING',attempt_count=attempt_count+1,last_error=NULL WHERE task_id=? AND status='PENDING'", taskId);
            if (claimed == 0) return;
            cleanup.cleanupTaskOrThrow(taskId, mapper.readValue(payloads.getFirst(), PATH_LIST));
            jdbc.update("UPDATE task_file_cleanup_job SET status='COMPLETED',completed_at=?,last_error=NULL WHERE task_id=?",
                    OffsetDateTime.now(), taskId);
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            jdbc.update("UPDATE task_file_cleanup_job SET status='PENDING',last_error=? WHERE task_id=?",
                    message.substring(0, Math.min(1000, message.length())), taskId);
            log.warn("TASK_FILE_CLEANUP_RETRY taskId={} reason={}", taskId, message);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.update("UPDATE task_file_cleanup_job SET status='PENDING' WHERE status='PROCESSING'");
        processPending();
    }

    @Scheduled(fixedDelayString = "${game-narrator.cleanup.task-file-retry-ms:60000}")
    public void processPending() {
        jdbc.query("SELECT task_id FROM task_file_cleanup_job WHERE status='PENDING' ORDER BY created_at LIMIT 20",
                (rs, row) -> rs.getObject(1, UUID.class)).forEach(this::process);
    }
}
