package cn.longer233.gamenarrator.task;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs only against the disposable PostgreSQL service provisioned by CI. */
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRESQL_INTEGRATION", matches = "true")
class PostgreSqlProductionIntegrationTest {
    private final String url = required("POSTGRES_URL");
    private final String user = required("POSTGRES_USER");
    private final String password = required("POSTGRES_PASSWORD");

    @Test
    void fullHistoryMigratesAndEnforcesTenantIntegrityAndOptimisticUpdates() throws Exception {
        var flyway = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration-postgresql")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        var result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isPositive();

        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        try (var connection = DriverManager.getConnection(url, user, password)) {
            try (var insertUser = connection.prepareStatement("""
                    INSERT INTO app_user(id,username,display_name,password_hash,role,status,created_at,updated_at)
                    VALUES(?,?,?,?,?,?,?,?)
                    """)) {
                insertUser.setObject(1, owner); insertUser.setString(2, "postgres-owner");
                insertUser.setString(3, "PostgreSQL Owner"); insertUser.setString(4, "hash");
                insertUser.setString(5, "USER"); insertUser.setString(6, "ACTIVE");
                insertUser.setObject(7, now); insertUser.setObject(8, now); insertUser.executeUpdate();
                insertUser.setObject(1, otherOwner); insertUser.setString(2, "postgres-other");
                insertUser.setString(3, "PostgreSQL Other"); insertUser.executeUpdate();
            }
            try (var insertProject = connection.prepareStatement("""
                    INSERT INTO video_project(id,owner_id,name,game_category,commentary_style,status,created_at,updated_at,version)
                    VALUES(?,?,?,?,?,?,?,?,0)
                    """)) {
                insertProject.setObject(1, project); insertProject.setObject(2, owner);
                insertProject.setString(3, "Production integration project");
                insertProject.setString(4, "ACTION"); insertProject.setString(5, "ANIME_THEATER");
                insertProject.setString(6, "DRAFT"); insertProject.setObject(7, now); insertProject.setObject(8, now);
                assertThat(insertProject.executeUpdate()).isOne();
            }
            try (var update = connection.prepareStatement("""
                    UPDATE video_project SET name=?,version=version+1,updated_at=?
                    WHERE id=? AND owner_id=? AND version=?
                    """)) {
                update.setString(1, "Updated once"); update.setObject(2, now.plusSeconds(1));
                update.setObject(3, project); update.setObject(4, owner); update.setLong(5, 0);
                assertThat(update.executeUpdate()).isOne();
                update.setString(1, "Stale overwrite");
                assertThat(update.executeUpdate()).as("stale version cannot overwrite PostgreSQL state").isZero();
                update.setObject(4, otherOwner); update.setLong(5, 1);
                assertThat(update.executeUpdate()).as("another tenant cannot update the project").isZero();
            }
            assertThatThrownBy(() -> {
                try (var invalid = connection.prepareStatement("""
                        INSERT INTO video_project(id,owner_id,name,game_category,commentary_style,status,created_at,updated_at,version)
                        VALUES(?,?,?,?,?,?,?,?,0)
                        """)) {
                    invalid.setObject(1, UUID.randomUUID()); invalid.setObject(2, UUID.randomUUID());
                    invalid.setString(3, "Orphan"); invalid.setString(4, "ACTION");
                    invalid.setString(5, "ANIME_THEATER"); invalid.setString(6, "DRAFT");
                    invalid.setObject(7, now); invalid.setObject(8, now); invalid.executeUpdate();
                }
            }).as("PostgreSQL must enforce project ownership foreign keys").isInstanceOf(java.sql.SQLException.class);

            try (var statement = connection.createStatement();
                 var migrations = statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE success=TRUE")) {
                assertThat(migrations.next()).isTrue();
                assertThat(migrations.getInt(1)).isGreaterThanOrEqualTo(40);
            }
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
