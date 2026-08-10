package cn.longer233.gamenarrator.community;

import cn.longer233.gamenarrator.effect.EffectPresetCatalog;
import cn.longer233.gamenarrator.event.GameKnowledgePackService;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommunityEcosystemServiceTest {
    @TempDir Path temporary;

    @Test
    void publishesAndInstallsEditingStyle() {
        JdbcTemplate jdbc = migrated("community");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        EffectPresetCatalog styles = new EffectPresetCatalog(mapper, temporary.toString());
        CommunityResourceService service = new CommunityResourceService(jdbc, mapper,
                mock(GameKnowledgePackService.class), styles);

        CommunityResourceView published = service.publishStyle("HUMOROUS",
                new PublishCommunityResourceRequest("测试作者", "CC-BY-4.0", List.of("搞笑", "快切")));

        assertThat(published.resourceType()).isEqualTo("EDITING_STYLE");
        assertThat(published.tags()).containsExactly("搞笑", "快切");
        assertThat(service.payload(published.id()).path("code").asText()).isEqualTo("HUMOROUS");
        service.install(published.id());
        assertThat(service.list("EDITING_STYLE")).hasSize(1);
        assertThat(service.list("EDITING_STYLE").getFirst().installCount()).isEqualTo(1);
    }

    @Test
    void generatesFourStrategiesWithoutCopyingSourceVideo() {
        JdbcTemplate jdbc = migrated("variants");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        VideoTask task = new VideoTask("同源录像", "动作", CommentaryStyle.PASSIONATE,
                120, "测试", temporary.resolve("source.mp4").toString());
        jdbc.update("""
                INSERT INTO video_tasks(id,name,game_category,commentary_style,target_duration_seconds,task_brief,
                source_video_path,status,storyboard_review_enabled,storyboard_approved,cloud_vision_enabled,
                ai_script_enabled,ai_voice_enabled,auto_assets_enabled,automatic_generation_enabled,
                editing_scope,created_at,version)
                VALUES(?,?,?,?,?,?,?,'READY',FALSE,TRUE,FALSE,FALSE,FALSE,FALSE,TRUE,'FULL_VIDEO',CURRENT_TIMESTAMP,0)
                """, task.getId(), task.getName(), task.getGameCategory(), task.getCommentaryStyle().name(),
                task.getTargetDurationSeconds(), task.getTaskBrief(), task.getSourceVideoPath());
        VideoTaskRepository repository = mock(VideoTaskRepository.class);
        when(repository.findById(task.getId())).thenReturn(Optional.of(task));
        CreativeVariantService service = new CreativeVariantService(jdbc, mapper, repository);

        var variants = service.generate(task.getId());

        assertThat(variants).extracting(CreativeVariantView::variantType)
                .containsExactly("STORY", "GUIDE", "COMEDY", "REVIEW");
        assertThat(variants).allSatisfy(item -> {
            assertThat(item.strategy().path("sourceReuse").asBoolean()).isTrue();
            assertThat(item.strategy().path("sourceVideoPath").asText()).isEqualTo(task.getSourceVideoPath());
        });
    }

    private JdbcTemplate migrated(String name) {
        String url = "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        return new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
    }
}
