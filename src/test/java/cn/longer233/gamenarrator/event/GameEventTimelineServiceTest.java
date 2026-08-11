package cn.longer233.gamenarrator.event;

import cn.longer233.gamenarrator.task.domain.CommentaryStyle;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GameEventTimelineServiceTest {
    @TempDir Path temp;

    @Test
    void buildsExplainableBossEventAndRegeneratesOnlyAfterARealConfirmedFactChange() throws Exception {
        String url = "jdbc:h2:mem:event-service;DB_CLOSE_DELAY=-1";
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID taskId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO video_tasks(id,name,game_category,commentary_style,target_duration_seconds,
                    task_brief,source_video_path,status,created_at)
                VALUES(?,?,?,?,?,?,?,?,?)
                """, taskId, "Boss test", "ACTION", "ANIME_THEATER", 60,
                "test", temp.resolve("source.mp4").toString(), "READY", Timestamp.from(Instant.now()));
        VideoTaskRepository repository = mock(VideoTaskRepository.class);
        when(repository.findById(taskId)).thenReturn(Optional.of(new VideoTask(
                "Boss test", "ACTION", CommentaryStyle.ANIME_THEATER, 60, "test", "source.mp4")));
        ObjectMapper mapper = new ObjectMapper();
        Path visual = temp.resolve("visual.json");
        Path highlights = temp.resolve("highlights.json");
        Files.writeString(visual, """
                {"frames":[{"index":3,"timestampSeconds":42.0,"imagePath":"frame.jpg",
                "description":"画面显示 Boss defeated，进入结算界面","eventType":"战斗","excitementScore":92,
                "ocrText":"VICTORY","rawJson":"{}"}]}
                """);
        Files.writeString(highlights, """
                {"clips":[{"sourceFrameIndex":3,"startSeconds":36.0,"endSeconds":48.0,
                "anchorSeconds":42.0,"eventType":"战斗","description":"Boss 战结束","sourceScore":92,
                "finalScore":95,"locked":false,"excluded":false}]}
                """);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        GameEventTimelineService service = new GameEventTimelineService(jdbc, mapper, repository, null, publisher);

        var events = service.rebuild(taskId, visual, highlights);

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().eventType()).isEqualTo("BOSS_DEFEATED");
        assertThat(events.getFirst().confirmationStatus()).isEqualTo("AI_SUGGESTED");
        assertThat(events.getFirst().evidence()).extracting(GameEventEvidence::sourceType)
                .contains("FRAME_DESCRIPTION", "OCR", "HIGHLIGHT_SCORE");
        assertThat(service.confirmedFacts(taskId)).isEmpty();

        GameEventView confirmed = service.update(taskId, events.getFirst().id(), new UpdateGameEventRequest(
                "BOSS_DEFEATED", "Boss 已被击败并进入结算", 100, "CONFIRMED"));
        verify(publisher).publishEvent(new ConfirmedGameEventChanged(taskId, events.getFirst().id()));
        clearInvocations(publisher);
        service.update(taskId, events.getFirst().id(), new UpdateGameEventRequest(
                confirmed.eventType(), confirmed.description(), confirmed.importance(), confirmed.confirmationStatus()));
        verifyNoInteractions(publisher);
        assertThat(service.confirmedFacts(taskId)).singleElement()
                .satisfies(fact -> assertThat(fact.description()).contains("进入结算"));
    }
}
