package cn.longer233.gamenarrator.task;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
                assertThat(migrations.getInt(1)).isEqualTo(41);
            }
        }
    }

    @Test
    void version224BaselineUpgradeMatchesFullMigrationHistory() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String fullSchema = "full_" + suffix;
        String baselineSchema = "baseline_" + suffix;
        try {
            var full = migration(fullSchema, "classpath:db/migration-postgresql");
            var baseline = migration(baselineSchema,
                    "classpath:db/baseline-postgresql", "classpath:db/migration-postgresql");

            assertThat(full.migrate().success).isTrue();
            assertThat(baseline.migrate().success).isTrue();
            full.validate();
            baseline.validate();

            assertThat(appliedVersions(fullSchema)).hasSize(41).endsWith("41");
            assertThat(appliedVersions(baselineSchema)).containsExactly("39", "40", "41");
            assertThat(structureSignature(fullSchema)).containsExactlyElementsOf(structureSignature(baselineSchema));
        } finally {
            dropSchema(fullSchema);
            dropSchema(baselineSchema);
        }
    }

    private Flyway migration(String schema, String... locations) {
        return Flyway.configure()
                .dataSource(url, user, password)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations(locations)
                .cleanDisabled(false)
                .load();
    }

    private List<String> appliedVersions(String schema) throws Exception {
        List<String> versions = new ArrayList<>();
        try (var connection = DriverManager.getConnection(url, user, password);
             var query = connection.prepareStatement("""
                     SELECT version FROM flyway_schema_history
                     WHERE success = TRUE AND version IS NOT NULL
                     ORDER BY installed_rank
                     """)) {
            connection.setSchema(schema);
            try (var rows = query.executeQuery()) {
                while (rows.next()) versions.add(rows.getString(1));
            }
        }
        return versions;
    }

    private List<String> structureSignature(String schema) throws Exception {
        List<String> signature = new ArrayList<>();
        try (var connection = DriverManager.getConnection(url, user, password)) {
            collect(signature, connection, """
                    SELECT 'column|' || table_name || '|' || column_name || '|' || data_type || '|' || is_nullable
                    FROM information_schema.columns
                    WHERE table_schema = ? AND table_name <> 'flyway_schema_history'
                    ORDER BY table_name, ordinal_position
                    """, schema);
            collect(signature, connection, """
                    SELECT 'constraint|' || table_name || '|' || constraint_name || '|' || constraint_type
                    FROM information_schema.table_constraints
                    WHERE table_schema = ?
                      AND constraint_name !~ '^[0-9]+_[0-9]+_[0-9]+_not_null$'
                    ORDER BY table_name, constraint_name
                    """, schema);
            collect(signature, connection, """
                    SELECT 'index|' || tablename || '|' || indexname || '|' ||
                           regexp_replace(indexdef, ' ON [^ ]+\\.', ' ON <schema>.')
                    FROM pg_indexes
                    WHERE schemaname = ? AND tablename <> 'flyway_schema_history'
                    ORDER BY tablename, indexname
                    """, schema);
        }
        return signature;
    }

    private static void collect(List<String> target, java.sql.Connection connection, String sql, String schema)
            throws Exception {
        try (var query = connection.prepareStatement(sql)) {
            query.setString(1, schema);
            try (var rows = query.executeQuery()) {
                while (rows.next()) target.add(rows.getString(1));
            }
        }
    }

    private void dropSchema(String schema) throws Exception {
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
