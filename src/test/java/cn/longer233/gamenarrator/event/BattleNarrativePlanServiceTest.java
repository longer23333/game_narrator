package cn.longer233.gamenarrator.event;

import cn.longer233.gamenarrator.script.ScriptWorkspaceService;
import cn.longer233.gamenarrator.script.StoryboardSegmentView;
import cn.longer233.gamenarrator.script.StoryboardView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BattleNarrativePlanServiceTest {
    @Test
    void plansFiveActsFromConfirmedEventsAndAppliesTheSameFingerprint() {
        String url = "jdbc:h2:mem:narrative-plan;DB_CLOSE_DELAY=-1";
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID taskId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO video_tasks(id,name,game_category,commentary_style,target_duration_seconds,
                    task_brief,source_video_path,status,created_at)
                VALUES(?,?,?,?,?,?,?,?,?)
                """, taskId, "Boss", "ACTION", "ANIME_THEATER", 60,
                "test", "source.mp4", "READY", Timestamp.from(Instant.now()));
        GameEventTimelineService events = mock(GameEventTimelineService.class);
        ScriptWorkspaceService workspace = mock(ScriptWorkspaceService.class);
        List<GameEventView> confirmed = List.of(
                event(taskId, "BOSS_BATTLE", "战斗开始", 0, 8, 60),
                event(taskId, "PLAYER_CRITICAL", "玩家进入危险状态", 8, 16, 82),
                event(taskId, "PHASE_TRANSITION", "Boss 进入第二阶段", 16, 24, 85),
                event(taskId, "SPECIAL_ATTACK", "关键攻击命中", 24, 32, 94),
                event(taskId, "BOSS_DEFEATED", "Boss 已被击败", 32, 40, 100));
        when(events.list(taskId)).thenReturn(confirmed);
        when(workspace.storyboard(taskId)).thenReturn(new StoryboardView("title", "synopsis", true, false,
                List.of(segment(1, 0, 8), segment(2, 8, 16), segment(3, 16, 24),
                        segment(4, 24, 32), segment(5, 32, 40))));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        BattleNarrativePlanService service = new BattleNarrativePlanService(jdbc, mapper, events, workspace);

        BattleNarrativePlanView plan = service.generate(taskId);

        assertThat(plan.beats()).extracting(NarrativeBeat::stage).containsExactly(
                NarrativeStage.SETUP, NarrativeStage.CRISIS, NarrativeStage.REVERSAL,
                NarrativeStage.CLIMAX, NarrativeStage.RESULT);
        assertThat(plan.beats()).extracting(NarrativeBeat::clipIndex).containsExactly(1, 2, 3, 4, 5);
        assertThat(plan.beats().get(3).musicIntensity()).isEqualTo(1.0);
        assertThat(service.apply(taskId).applied()).isTrue();
        verify(workspace).applyNarrativeStructure(taskId, plan.beats());
    }

    @Test
    void keepsPlaceholderBeatWithoutClipWhenReviewedOverridePointsToMissingClip() throws Exception {
        String url = "jdbc:h2:mem:narrative-plan-placeholder;DB_CLOSE_DELAY=-1";
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID taskId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO video_tasks(id,name,game_category,commentary_style,target_duration_seconds,
                    task_brief,source_video_path,status,created_at)
                VALUES(?,?,?,?,?,?,?,?,?)
                """, taskId, "Boss", "ACTION", "ANIME_THEATER", 60,
                "test", "source.mp4", "READY", Timestamp.from(Instant.now()));
        GameEventTimelineService events = mock(GameEventTimelineService.class);
        ScriptWorkspaceService workspace = mock(ScriptWorkspaceService.class);
        List<GameEventView> confirmed = List.of(event(taskId, "BOSS_BATTLE", "battle", 0, 8, 60));
        StoryboardView storyboard = new StoryboardView("title", "synopsis", true, false,
                List.of(segment(1, 0, 8)));
        when(events.list(taskId)).thenReturn(confirmed);
        when(workspace.storyboard(taskId)).thenReturn(storyboard);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        BattleNarrativePlanService service = new BattleNarrativePlanService(jdbc, mapper, events, workspace);
        service.generate(taskId);

        BattleNarrativePlanView reviewed = service.applyReviewed(taskId, mapper.readTree("""
                {"narrativeBeats":[{"stage":"CRISIS","clipIndex":999}]}
                """));

        assertThat(reviewed.beats()).filteredOn(beat -> beat.stage() == NarrativeStage.CRISIS)
                .singleElement().extracting(NarrativeBeat::clipIndex).isNull();
        verify(workspace).applyNarrativeStructure(taskId, reviewed.beats());
    }

    private GameEventView event(UUID taskId, String type, String description,
                                double start, double end, int importance) {
        return new GameEventView(UUID.randomUUID(), taskId, start, end, (start + end) / 2,
                type, .9, importance, description, List.of(), "CONFIRMED", true,
                "boss-battle-v1", OffsetDateTime.now());
    }

    private StoryboardSegmentView segment(int index, double start, double end) {
        return new StoryboardSegmentView(index, start, end, "旁白", "字幕", "", "event",
                "description", 80 + index, false, false);
    }
}
