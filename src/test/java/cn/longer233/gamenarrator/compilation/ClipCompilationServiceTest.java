package cn.longer233.gamenarrator.compilation;

import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.identity.LocalUserContext;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClipCompilationServiceTest {
    @Test
    void isolatesCompilationsAndRequiresTasksOwnedByCurrentUser() {
        DriverManagerDataSource source = migrated("compilation-owner-isolation");
        JdbcTemplate template = new JdbcTemplate(source);
        UUID owner = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        insertUser(template, owner, "compilation-owner");
        insertUser(template, intruder, "compilation-intruder");
        UUID ownerTask = insertTask(template, owner, "owner task");
        UUID intruderTask = insertTask(template, intruder, "intruder task");
        AtomicReference<UUID> activeUser = new AtomicReference<>(owner);
        CurrentUserContext currentUser = mock(CurrentUserContext.class);
        when(currentUser.userId()).thenAnswer(ignored -> activeUser.get());
        ClipCompilationService service = new ClipCompilationService(JdbcClient.create(source), currentUser);

        ClipCompilationService.CompilationView ownerCompilation = service.create("owner compilation");
        ClipCompilationService.CompilationView withItem = service.add(ownerCompilation.id(), ownerTask, 0);
        assertThat(withItem.items()).extracting(ClipCompilationService.CompilationItemView::taskId)
                .containsExactly(ownerTask);

        activeUser.set(intruder);
        assertThat(service.list()).isEmpty();
        assertThatThrownBy(() -> service.find(ownerCompilation.id()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("合集不存在");
        assertThatThrownBy(() -> service.add(ownerCompilation.id(), intruderTask, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("合集不存在");

        ClipCompilationService.CompilationView intruderCompilation = service.create("intruder compilation");
        assertThatThrownBy(() -> service.add(intruderCompilation.id(), ownerTask, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("任务不存在");
        assertThatThrownBy(() -> service.reorder(ownerCompilation.id(), List.of(withItem.items().getFirst().id())))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("合集不存在");

        assertThat(template.queryForObject("SELECT COUNT(*) FROM clip_compilation_item", Integer.class)).isOne();
    }

    @Test
    void v44BackfillsLegacyCompilationsAndEnforcesOwnerSchema() {
        String url = "jdbc:h2:mem:compilation-owner-migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("43").load().migrate();
        JdbcTemplate template = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        UUID compilationId = UUID.randomUUID();
        template.update("INSERT INTO clip_compilation(id,name,created_at) VALUES(?,?,?)",
                compilationId, "legacy compilation", Timestamp.from(Instant.now()));

        Flyway.configure().dataSource(url, "sa", "").target("44").load().migrate();

        assertThat(template.queryForObject("SELECT owner_id FROM clip_compilation WHERE id=?", UUID.class,
                compilationId)).isEqualTo(LocalUserContext.LOCAL_USER_ID);
        assertThat(template.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_name='CLIP_COMPILATION' AND column_name='OWNER_ID'
                """, String.class)).isEqualTo("NO");
        assertThat(template.queryForObject("""
                SELECT COUNT(*) FROM information_schema.indexes
                WHERE table_name='CLIP_COMPILATION' AND index_name='IDX_CLIP_COMPILATION_OWNER_TIME'
                """, Integer.class)).isOne();
        assertThatThrownBy(() -> template.update(
                "INSERT INTO clip_compilation(id,owner_id,name,created_at) VALUES(?,?,?,?)",
                UUID.randomUUID(), UUID.randomUUID(), "invalid owner", Timestamp.from(Instant.now())))
                .hasRootCauseInstanceOf(java.sql.SQLException.class);
    }

    private static DriverManagerDataSource migrated(String name) {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(source).load().migrate();
        return source;
    }

    private static void insertUser(JdbcTemplate jdbc, UUID id, String username) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO app_user(id,username,display_name,password_hash,role,status,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?)
                """, id, username, username, null, "USER", "ACTIVE", now, now);
    }

    private static UUID insertTask(JdbcTemplate jdbc, UUID ownerId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO video_tasks(id,name,game_category,commentary_style,target_duration_seconds,
                    task_brief,source_video_path,status,created_at,owner_id)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """, id, name, "ACTION", "ANIME_THEATER", 60, "test", "source.mp4", "READY",
                Instant.now(), ownerId);
        return id;
    }
}
