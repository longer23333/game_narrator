package cn.longer233.gamenarrator.pipeline;

import cn.longer233.gamenarrator.vision.VisionAnalyzer;
import cn.longer233.gamenarrator.voice.VoiceSynthesizer;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;

class WaitingTaskRetrySchedulerTest {
    @Test
    void doesNotRestartWaitingTaskAfterItWasMovedToTrash() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE video_tasks(id UUID,project_id UUID,status VARCHAR,deleted_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE video_project(id UUID,latest_run_id UUID,deleted_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE generation_run(id UUID,status VARCHAR)");
        jdbc.execute("CREATE TABLE stage_run(generation_run_id UUID,status VARCHAR,stage_type VARCHAR)");
        UUID task = UUID.randomUUID(), project = UUID.randomUUID(), run = UUID.randomUUID();
        jdbc.update("INSERT INTO video_tasks VALUES(?,?,?,?)", task, project, "PROCESSING",
                Timestamp.from(Instant.now()));
        jdbc.update("INSERT INTO video_project VALUES(?,?,?)", project, run, Timestamp.from(Instant.now()));
        jdbc.update("INSERT INTO generation_run VALUES(?,?)", run, "WAITING");
        jdbc.update("INSERT INTO stage_run VALUES(?,?,?)", run, "WAITING", "VIDEO_UNDERSTANDING");
        VideoTaskEngine engine = mock(VideoTaskEngine.class);
        VisionAnalyzer vision = mock(VisionAnalyzer.class);
        when(vision.available()).thenReturn(true);

        new WaitingTaskRetryScheduler(jdbc, engine, vision, mock(VoiceSynthesizer.class))
                .retryReadyDependencies();

        verifyNoInteractions(engine);
    }
}
