package cn.longer233.gamenarrator.personalization;

import cn.longer233.gamenarrator.script.ScriptSegment;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DirectorProfileServiceTest {
    @Test
    void learnsDurationDensityAndEffectFromFinalEdit() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:director-profile;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID taskId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO video_tasks(id,name,game_category,commentary_style,target_duration_seconds,
                task_brief,source_video_path,status,created_at) VALUES(?,?,?,?,?,?,?,?,?)
                """, taskId, "test", "ACTION", "ANIME_THEATER", 60, "test", "source.mp4", "READY", OffsetDateTime.now());
        DirectorProfileService service = new DirectorProfileService(jdbc, new ObjectMapper());

        service.recordScriptEdit(taskId,
                new ScriptSegment(1, 0, 10, "十个字的初始文案内容", "", "cut"),
                new ScriptSegment(1, 0, 12, "更长一些的最终文案内容用于表达", "", "zoom"));

        DirectorProfileView profile = service.profile();
        assertThat(profile.decisionCount()).isEqualTo(1);
        assertThat(profile.preferredDurationRatio()).isEqualTo(1.2);
        assertThat(profile.preferredEffects()).containsExactly("zoom");
        assertThat(profile.summary()).contains("1 次最终修改");
    }
}
