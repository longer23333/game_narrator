package cn.longer233.gamenarrator.pipeline;

import cn.longer233.gamenarrator.vision.OllamaVisionClient;
import cn.longer233.gamenarrator.voice.VoiceGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class WaitingTaskRetryScheduler {
    private static final Logger log = LoggerFactory.getLogger(WaitingTaskRetryScheduler.class);
    private final JdbcTemplate jdbc;
    private final VideoTaskEngine engine;
    private final OllamaVisionClient visionClient;
    private final VoiceGenerator voiceGenerator;

    public WaitingTaskRetryScheduler(JdbcTemplate jdbc, VideoTaskEngine engine,
                                     OllamaVisionClient visionClient, VoiceGenerator voiceGenerator) {
        this.jdbc = jdbc;
        this.engine = engine;
        this.visionClient = visionClient;
        this.voiceGenerator = voiceGenerator;
    }

    @Scheduled(fixedDelayString = "${game-narrator.pipeline.waiting-retry-delay-ms:30000}",
            initialDelayString = "${game-narrator.pipeline.waiting-retry-initial-delay-ms:30000}")
    public void retryReadyDependencies() {
        var waiting = jdbc.query("""
                SELECT DISTINCT task.id, stage.stage_type
                FROM video_tasks task
                JOIN video_project project ON project.id=task.project_id
                JOIN generation_run run ON run.id=project.latest_run_id AND run.status='WAITING'
                JOIN stage_run stage ON stage.generation_run_id=run.id AND stage.status='WAITING'
                WHERE task.status='PROCESSING'
                """, (rs, row) -> new WaitingTask(rs.getObject(1, UUID.class), rs.getString(2)));
        for (WaitingTask task : waiting) {
            boolean ready = switch (task.stageType()) {
                case "VIDEO_UNDERSTANDING" -> visionClient.available();
                case "VOICE_GENERATION" -> voiceGenerator.available();
                default -> false;
            };
            if (ready) {
                log.info("WAITING_TASK_RETRY_SUBMIT taskId={} stage={}", task.id(), task.stageType());
                engine.start(task.id());
            }
        }
    }

    private record WaitingTask(UUID id, String stageType) { }
}
