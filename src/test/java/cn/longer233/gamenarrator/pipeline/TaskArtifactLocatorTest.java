package cn.longer233.gamenarrator.pipeline;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TaskArtifactLocatorTest {
    @Test
    void prefersLatestLiveArtifactAndFallsBackForLegacyTask() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:artifact-locator-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE video_tasks(id UUID PRIMARY KEY,project_id UUID,rendered_video_path VARCHAR(500))");
        jdbc.execute("CREATE TABLE artifact(id UUID PRIMARY KEY,project_id UUID,artifact_type VARCHAR(40),storage_key VARCHAR(1000),created_at TIMESTAMP WITH TIME ZONE,deleted_at TIMESTAMP WITH TIME ZONE)");
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        jdbc.update("INSERT INTO video_tasks(id,project_id,rendered_video_path) VALUES(?,?,?)",
                taskId, projectId, "legacy.mp4");
        TaskArtifactLocator locator = new TaskArtifactLocator(jdbc);

        assertThat(locator.latest(taskId, "RENDERED_VIDEO")).contains(Path.of("legacy.mp4").toAbsolutePath().normalize());

        jdbc.update("INSERT INTO artifact(id,project_id,artifact_type,storage_key,created_at,deleted_at) VALUES(?,?,?,?,?,NULL)",
                UUID.randomUUID(), projectId, "RENDERED_VIDEO", "indexed.mp4", OffsetDateTime.now());
        assertThat(locator.latest(taskId, "RENDERED_VIDEO")).contains(Path.of("indexed.mp4").toAbsolutePath().normalize());
        assertThat(locator.latest(taskId, "UNKNOWN_TYPE")).isEmpty();
    }
}
