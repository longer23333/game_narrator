package cn.longer233.gamenarrator.pipeline;

import cn.longer233.gamenarrator.task.domain.ProcessingStageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StageCheckpointServiceTest {
    @TempDir Path temporary;

    @Test
    void preservesValidCompletedCheckpointIdempotently() throws Exception {
        Fixture fixture = fixture("valid");
        Path source = write("source.mp4", "source");
        Path manifest = write("scenes.json", "{\"scenes\":[]}");
        fixture.task(source, ProcessingStageType.SCENE_DETECTION);
        fixture.artifact("SCENE_MANIFEST", manifest, "application/json");

        var first = fixture.service.reconcile(fixture.taskId);
        var second = fixture.service.reconcile(fixture.taskId);

        assertThat(first.valid()).isTrue();
        assertThat(second.valid()).isTrue();
        assertThat(fixture.status(ProcessingStageType.SCENE_DETECTION)).isEqualTo("COMPLETED");
        assertThat(fixture.activeArtifacts()).isEqualTo(1);
    }

    @Test
    void checksumMismatchRestartsAtEarliestBrokenStageAndInvalidatesDownstream() throws Exception {
        Fixture fixture = fixture("tampered");
        Path source = write("source.mp4", "source");
        Path scenes = write("scenes.json", "{\"scenes\":[]}");
        Path vision = write("vision.json", "{\"frames\":[]}");
        fixture.task(source, ProcessingStageType.VIDEO_UNDERSTANDING);
        fixture.artifact("SCENE_MANIFEST", scenes, "application/json");
        fixture.artifact("VISION_ANALYSIS", vision, "application/json");
        Files.writeString(scenes, "{\"tampered\":true}");

        var result = fixture.service.reconcile(fixture.taskId);

        assertThat(result.valid()).isFalse();
        assertThat(result.restartStage()).isEqualTo(ProcessingStageType.SCENE_DETECTION);
        assertThat(fixture.status(ProcessingStageType.VIDEO_INGESTION)).isEqualTo("COMPLETED");
        assertThat(fixture.status(ProcessingStageType.SCENE_DETECTION)).isEqualTo("PENDING");
        assertThat(fixture.status(ProcessingStageType.VIDEO_UNDERSTANDING)).isEqualTo("PENDING");
        assertThat(fixture.activeArtifacts()).isZero();
        assertThat(fixture.string("scene_manifest_path")).isNull();
        assertThat(fixture.string("visual_analysis_path")).isNull();
    }

    @Test
    void missingSourceRestartsWholePipeline() throws Exception {
        Fixture fixture = fixture("missing-source");
        Path missing = temporary.resolve("missing.mp4");
        fixture.task(missing, ProcessingStageType.TRANSCRIPTION);

        var result = fixture.service.reconcile(fixture.taskId);

        assertThat(result.restartStage()).isEqualTo(ProcessingStageType.VIDEO_INGESTION);
        assertThat(fixture.status(ProcessingStageType.VIDEO_INGESTION)).isEqualTo("PENDING");
        assertThat(fixture.status(ProcessingStageType.TRANSCRIPTION)).isEqualTo("PENDING");
    }

    private Path write(String name, String contents) throws Exception {
        Path path = temporary.resolve(name);
        Files.writeString(path, contents);
        return path;
    }

    private Fixture fixture(String name) {
        var source = new DriverManagerDataSource("jdbc:h2:mem:checkpoint-" + name + ";DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(source).load().migrate();
        return new Fixture(new JdbcTemplate(source));
    }

    private static final class Fixture {
        private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
        private final JdbcTemplate jdbc;
        private final StageCheckpointService service;
        private final UUID taskId = UUID.randomUUID();
        private final UUID revisionId = UUID.randomUUID();
        private final UUID runId = UUID.randomUUID();

        private Fixture(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
            this.service = new StageCheckpointService(jdbc, new ObjectMapper());
        }

        private void task(Path source, ProcessingStageType completedThrough) {
            var now = Timestamp.from(Instant.now());
            jdbc.update("INSERT INTO video_project(id,owner_id,name,game_category,commentary_style,status,created_at,updated_at,version) VALUES(?,?,?,?,?,'READY',?,?,0)",
                    taskId, USER, "checkpoint", "ACTION", "ANIME_THEATER", now, now);
            jdbc.update("""
                    INSERT INTO video_tasks(id,name,game_category,commentary_style,target_duration_seconds,task_brief,
                    source_video_path,status,created_at,owner_id,project_id,version,storyboard_review_enabled,
                    storyboard_approved,cloud_vision_enabled,ai_script_enabled,ai_voice_enabled,auto_assets_enabled,
                    automatic_generation_enabled,editing_scope,processing_priority)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, taskId, "checkpoint", "ACTION", "ANIME_THEATER", 30, "test", source.toString(),
                    "COMPLETED", now, USER, taskId, 0, false, true, true, true, true, true, true, "FULL_VIDEO", 0);
            jdbc.update("INSERT INTO project_revision(id,project_id,revision_no,created_by,change_type,parameter_snapshot_json,manifest_json,manifest_schema_version,manifest_sha256,created_at) VALUES(?,?,1,?,'CREATE','{}','{}',1,?,?)",
                    revisionId, taskId, USER, "0".repeat(64), now);
            jdbc.update("UPDATE video_project SET current_revision_id=? WHERE id=?", revisionId, taskId);
            jdbc.update("INSERT INTO generation_run(id,project_id,user_id,input_revision_id,run_type,status,trigger_source,created_at) VALUES(?,?,?,?,'FULL_PIPELINE','COMPLETED','SYSTEM',?)",
                    runId, taskId, USER, revisionId, now);
            int sequence = 1;
            for (ProcessingStageType stage : ProcessingStageType.values()) {
                String status = stage.ordinal() <= completedThrough.ordinal() ? "COMPLETED" : "PENDING";
                jdbc.update("INSERT INTO processing_stages(id,task_id,stage_type,sequence_number,status,progress) VALUES(?,?,?,?,?,?)",
                        UUID.randomUUID(), taskId, stage.name(), sequence++, status, status.equals("COMPLETED") ? 100 : 0);
            }
            jdbc.update("UPDATE video_tasks SET scene_manifest_path=?,visual_analysis_path=? WHERE id=?",
                    source.resolveSibling("scenes.json").toString(), source.resolveSibling("vision.json").toString(), taskId);
        }

        private void artifact(String type, Path path, String mime) throws Exception {
            jdbc.update("""
                    INSERT INTO artifact(id,owner_id,project_id,revision_id,generation_run_id,artifact_type,storage_key,
                    mime_type,size_bytes,sha256,schema_version,temporary,created_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?,1,FALSE,?)
                    """, UUID.randomUUID(), USER, taskId, revisionId, runId, type, path.toString(), mime,
                    Files.size(path), sha256(path), OffsetDateTime.now(ZoneOffset.UTC));
        }

        private String status(ProcessingStageType stage) {
            return jdbc.queryForObject("SELECT status FROM processing_stages WHERE task_id=? AND stage_type=?",
                    String.class, taskId, stage.name());
        }
        private int activeArtifacts() { return jdbc.queryForObject("SELECT COUNT(*) FROM artifact WHERE project_id=? AND deleted_at IS NULL", Integer.class, taskId); }
        private String string(String column) { return jdbc.queryForObject("SELECT " + column + " FROM video_tasks WHERE id=?", String.class, taskId); }
        private static String sha256(Path path) throws Exception {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        }
    }
}
