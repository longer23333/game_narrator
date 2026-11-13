package cn.longer233.gamenarrator.community;

import cn.longer233.gamenarrator.ai.AdaptiveAiChatClient;
import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.identity.LocalUserContext;
import cn.longer233.gamenarrator.task.domain.CommentaryStyle;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 多版本方案的来源必须诚实：内置模板不能被当成 AI 成果，AI 失败也不能悄悄退回模板。
 */
class CreativeVariantServiceTest {
    @TempDir Path temporary;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void presetVariantsAreMarkedAsTemplate() {
        Fixture fixture = fixture("variant-origin-template", "模板任务", mock(AdaptiveAiChatClient.class), false);

        List<CreativeVariantView> variants = fixture.service().generate(fixture.taskId());

        assertThat(variants).hasSize(4);
        assertThat(variants).allSatisfy(item -> {
            assertThat(item.strategy().path("origin").asText()).isEqualTo("TEMPLATE");
            assertThat(item.strategy().path("originLabel").asText()).contains("预设");
            assertThat(item.strategy().path("originNote").asText()).contains("未调用 AI");
        });
    }

    @Test
    void aiVariantsAreMarkedAsAiAndKeepBrief() throws Exception {
        AdaptiveAiChatClient assistant = mock(AdaptiveAiChatClient.class);
        when(assistant.chatJson(anyString(), anyList(), anyBoolean(), any(Duration.class)))
                .thenReturn(mapper.readTree("""
                        [{"variantType":"STORY","name":"剧情版","narrativeFocus":"复仇线","editingRule":"慢推镜头","targetDurationSeconds":90},
                         {"variantType":"GUIDE","name":"攻略版","narrativeFocus":"机制拆解","editingRule":"关键帧慢放","targetDurationSeconds":100},
                         {"variantType":"COMEDY","name":"搞笑版","narrativeFocus":"失误合集","editingRule":"快切","targetDurationSeconds":70},
                         {"variantType":"REVIEW","name":"复盘版","narrativeFocus":"决策回溯","editingRule":"保留上下文","targetDurationSeconds":110}]
                        """));
        Fixture fixture = fixture("variant-origin-ai", "AI 任务", assistant, false);

        List<CreativeVariantView> variants = fixture.service()
                .generate(fixture.taskId(), "做一个复仇主题的短视频");

        assertThat(variants).hasSize(4);
        assertThat(variants).allSatisfy(item -> {
            assertThat(item.strategy().path("origin").asText()).isEqualTo("AI");
            assertThat(item.strategy().path("originLabel").asText()).isEqualTo("AI 生成");
            assertThat(item.strategy().path("brief").asText()).isEqualTo("做一个复仇主题的短视频");
        });
        assertThat(variants).anySatisfy(item ->
                assertThat(item.strategy().path("narrativeFocus").asText()).isEqualTo("复仇线"));
    }

    @Test
    void aiFailureIsNotSilentlyDowngradedToTemplate() throws Exception {
        AdaptiveAiChatClient assistant = mock(AdaptiveAiChatClient.class);
        when(assistant.chatJson(anyString(), anyList(), anyBoolean(), any(Duration.class)))
                .thenThrow(new IllegalStateException("模型连接被拒绝"));
        Fixture fixture = fixture("variant-ai-failure", "失败任务", assistant, false);

        assertThatThrownBy(() -> fixture.service().generate(fixture.taskId(), "随便来一套"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI 创意方案生成失败")
                .hasMessageContaining("模型连接被拒绝");

        assertThat(countVariants(fixture)).isZero();
    }

    @Test
    void unreadableAiResponseIsRejectedInsteadOfFallingBack() throws Exception {
        AdaptiveAiChatClient assistant = mock(AdaptiveAiChatClient.class);
        when(assistant.chatJson(anyString(), anyList(), anyBoolean(), any(Duration.class)))
                .thenReturn(mapper.readTree("[]"));
        Fixture fixture = fixture("variant-ai-empty", "空响应任务", assistant, false);

        assertThatThrownBy(() -> fixture.service().generate(fixture.taskId(), "来一套"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI 没有返回可解析的方案");

        assertThat(countVariants(fixture)).isZero();
    }

    @Test
    void aiResponseWithoutVariantTypesIsRejected() throws Exception {
        AdaptiveAiChatClient assistant = mock(AdaptiveAiChatClient.class);
        when(assistant.chatJson(anyString(), anyList(), anyBoolean(), any(Duration.class)))
                .thenReturn(mapper.readTree("[{\"name\":\"不知道是什么版本\"}]"));
        Fixture fixture = fixture("variant-ai-no-type", "无类型任务", assistant, false);

        assertThatThrownBy(() -> fixture.service().generate(fixture.taskId(), "来一套"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少 variantType");

        assertThat(countVariants(fixture)).isZero();
    }

    @Test
    void missingAssistantFailsLoudly() {
        Fixture fixture = fixture("variant-no-assistant", "无 AI 任务", null, true);

        assertThatThrownBy(() -> fixture.service().generate(fixture.taskId(), "来一套"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI 创意助手未配置");

        assertThat(countVariants(fixture)).isZero();
    }

    @Test
    void partialAiResultFillsMissingTypesWithMarkedTemplate() throws Exception {
        AdaptiveAiChatClient assistant = mock(AdaptiveAiChatClient.class);
        when(assistant.chatJson(anyString(), anyList(), anyBoolean(), any(Duration.class)))
                .thenReturn(mapper.readTree(
                        "[{\"variantType\":\"STORY\",\"name\":\"剧情版\",\"narrativeFocus\":\"复仇线\",\"editingRule\":\"慢推\"}]"));
        Fixture fixture = fixture("variant-ai-partial", "部分任务", assistant, false);

        List<CreativeVariantView> variants = fixture.service().generate(fixture.taskId(), "只要剧情版");

        assertThat(variants).extracting(CreativeVariantView::variantType)
                .containsExactly("STORY", "GUIDE", "COMEDY", "REVIEW");
        assertThat(variants).filteredOn(item -> "STORY".equals(item.variantType()))
                .singleElement()
                .satisfies(item -> assertThat(item.strategy().path("origin").asText()).isEqualTo("AI"));
        assertThat(variants).filteredOn(item -> !"STORY".equals(item.variantType()))
                .hasSize(3)
                .allSatisfy(item -> {
                    assertThat(item.strategy().path("origin").asText()).isEqualTo("TEMPLATE");
                    assertThat(item.strategy().path("originNote").asText()).contains("AI 未提供该版本");
                });
    }

    private int countVariants(Fixture fixture) {
        return fixture.jdbc().queryForObject("SELECT COUNT(*) FROM creative_variant WHERE source_task_id=?",
                Integer.class, fixture.taskId());
    }

    private Fixture fixture(String schema, String taskName, AdaptiveAiChatClient assistant, boolean withoutAssistant) {
        JdbcTemplate jdbc = migrated(schema);
        VideoTask task = new VideoTask(taskName, "动作", CommentaryStyle.PASSIONATE, 120, "测试",
                temporary.resolve(taskName + ".mp4").toString());
        UUID ownerId = LocalUserContext.LOCAL_USER_ID;
        jdbc.update("""
                INSERT INTO video_tasks(id,name,game_category,commentary_style,target_duration_seconds,
                task_brief,source_video_path,status,created_at,owner_id)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """, task.getId(), task.getName(), task.getGameCategory(), task.getCommentaryStyle().name(),
                task.getTargetDurationSeconds(), task.getTaskBrief(), task.getSourceVideoPath(),
                "READY", Instant.now(), ownerId);
        CurrentUserContext current = mock(CurrentUserContext.class);
        when(current.userId()).thenReturn(ownerId);
        VideoTaskRepository repository = mock(VideoTaskRepository.class);
        when(repository.findByIdAndOwnerId(task.getId(), ownerId)).thenReturn(Optional.of(task));
        CreativeVariantService service = withoutAssistant
                ? new CreativeVariantService(jdbc, mapper, repository, current)
                : new CreativeVariantService(jdbc, mapper, repository, null, null, null, current, assistant);
        return new Fixture(jdbc, task.getId(), service);
    }

    private JdbcTemplate migrated(String name) {
        String url = "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        return new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
    }

    private record Fixture(JdbcTemplate jdbc, UUID taskId, CreativeVariantService service) { }
}
