package cn.longer233.gamenarrator.task.application;

import cn.longer233.gamenarrator.common.StorageCleanupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class TaskFileCleanupServiceTest {

    @Test
    void failedCleanupRemainsDurableAndCanBeRetried() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:cleanup-job;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE task_file_cleanup_job(task_id UUID PRIMARY KEY,artifact_paths CLOB NOT NULL,status VARCHAR(20),attempt_count INTEGER,last_error VARCHAR(1000),created_at TIMESTAMP WITH TIME ZONE,completed_at TIMESTAMP WITH TIME ZONE)");
        StorageCleanupService cleanup = mock(StorageCleanupService.class);
        TaskFileCleanupService service = new TaskFileCleanupService(jdbc, new ObjectMapper(), cleanup);
        UUID taskId = UUID.randomUUID();
        List<String> paths = List.of("data/tasks/example/render-work/clip.mp4");
        doThrow(new IllegalStateException("文件暂时被占用")).when(cleanup).cleanupTaskOrThrow(taskId, paths);

        service.enqueue(taskId, paths);
        service.process(taskId);

        assertThat(jdbc.queryForMap("SELECT status,attempt_count,last_error FROM task_file_cleanup_job WHERE task_id=?", taskId))
                .containsEntry("STATUS", "PENDING")
                .containsEntry("ATTEMPT_COUNT", 1)
                .hasEntrySatisfying("LAST_ERROR", value -> assertThat(value.toString()).contains("文件暂时被占用"));

        doNothing().when(cleanup).cleanupTaskOrThrow(taskId, paths);
        service.processPending();

        assertThat(jdbc.queryForMap("SELECT status,attempt_count,completed_at FROM task_file_cleanup_job WHERE task_id=?", taskId))
                .containsEntry("STATUS", "COMPLETED")
                .containsEntry("ATTEMPT_COUNT", 2)
                .hasEntrySatisfying("COMPLETED_AT", value -> assertThat(value).isNotNull());
    }
}
