package cn.longer233.gamenarrator.task;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import org.h2.tools.RunScript;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationTest {
    @Test
    void v42BackfillsLegacyTaskPathsWithoutDeletingColumns() throws Exception {
        String url = "jdbc:h2:mem:legacy-artifact-backfill;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("41").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO video_project(id,owner_id,name,game_category,commentary_style,status,created_at,updated_at)
                    VALUES(UUID 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                    UUID '00000000-0000-0000-0000-000000000001','legacy','ACTION','ANIME_THEATER','READY',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO video_tasks(id,owner_id,project_id,name,game_category,commentary_style,
                    target_duration_seconds,task_brief,source_video_path,status,created_at,rendered_video_path)
                    VALUES(UUID 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                    UUID '00000000-0000-0000-0000-000000000001',UUID 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                    'legacy','ACTION','ANIME_THEATER',30,'legacy','source.mp4','COMPLETED',CURRENT_TIMESTAMP,'legacy-final.mp4')
                    """);
        }

        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            var artifact = statement.executeQuery("""
                    SELECT artifact_type,storage_key,schema_version,size_bytes,sha256
                    FROM artifact WHERE project_id=UUID 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
                    """);
            assertThat(artifact.next()).isTrue();
            assertThat(artifact.getString("artifact_type")).isEqualTo("RENDERED_VIDEO");
            assertThat(artifact.getString("storage_key")).isEqualTo("legacy-final.mp4");
            assertThat(artifact.getInt("schema_version")).isZero();
            assertThat(artifact.getLong("size_bytes")).isZero();
            assertThat(artifact.getString("sha256")).isEqualTo("0".repeat(64));
            var legacyColumn = statement.executeQuery("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_name='VIDEO_TASKS' AND column_name='RENDERED_VIDEO_PATH'
                    """);
            assertThat(legacyColumn.next()).isTrue();
            assertThat(legacyColumn.getInt(1)).isOne();
        }
    }

    @Test
    void generatedH2BaselineMatchesVersion39MigrationSchema() throws Exception {
        String migratedUrl = "jdbc:h2:mem:history-through-v39;DB_CLOSE_DELAY=-1";
        String baselineUrl = "jdbc:h2:mem:generated-baseline;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(migratedUrl, "sa", "").target("39").load().migrate();
        try (var baselineConnection = DriverManager.getConnection(baselineUrl, "sa", "")) {
            RunScript.execute(baselineConnection, Files.newBufferedReader(Path.of(
                    "src/main/resources/db/baseline-h2/B39__version_2_2_4_baseline.sql")));
        }
        assertThat(schemaObjects(baselineUrl)).isEqualTo(schemaObjects(migratedUrl));
    }

    @Test
    void v40AddsMemeTimelineParametersAndLayerIndex() throws Exception {
        String url = "jdbc:h2:mem:meme-slots;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            var columns = statement.executeQuery("""
                    SELECT column_name,column_default FROM information_schema.columns
                    WHERE table_name='STORYBOARD_ASSET_PLACEMENT'
                    AND column_name IN ('START_OFFSET_SECONDS','END_OFFSET_SECONDS','SCALE_PERCENT','ANIMATION_NAME','Z_INDEX')
                    """);
            int count = 0;
            while (columns.next()) count++;
            assertThat(count).isEqualTo(5);
            var index = statement.executeQuery("""
                    SELECT COUNT(*) FROM information_schema.indexes
                    WHERE table_name='STORYBOARD_ASSET_PLACEMENT' AND index_name='IDX_STORYBOARD_ASSET_LAYER'
                    """);
            assertThat(index.next()).isTrue();
            assertThat(index.getInt(1)).isOne();
        }
    }

    @Test
    void v41AddsSoundEffectMixParameters() throws Exception {
        String url = "jdbc:h2:mem:sfx-timeline;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var columns = statement.executeQuery("""
                     SELECT column_name,column_default FROM information_schema.columns
                     WHERE table_name='STORYBOARD_ASSET_PLACEMENT'
                     AND column_name IN ('VOLUME_PERCENT','FADE_IN_SECONDS','FADE_OUT_SECONDS')
                     """)) {
            int count = 0;
            while (columns.next()) count++;
            assertThat(count).isEqualTo(3);
        }
    }

    @Test
    void backupRestoreDrillRecoversPreMigrationDataAndSchema() throws Exception {
        String sourceUrl = "jdbc:h2:mem:rollback-source;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(sourceUrl, "sa", "").target("37").load().migrate();
        Path backup = Files.createTempFile("game-narrator-v37-", ".sql");
        try (var connection = DriverManager.getConnection(sourceUrl, "sa", ""); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO app_user(id,username,display_name,password_hash,role,status,created_at,updated_at) VALUES (UUID 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','recovery-user','Recovery','x','USER','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            statement.execute("SCRIPT TO '" + backup.toAbsolutePath().toString().replace("'", "''") + "'");
        }
        Flyway.configure().dataSource(sourceUrl, "sa", "").load().migrate();

        String restoredUrl = "jdbc:h2:mem:rollback-restored;DB_CLOSE_DELAY=-1";
        try (var restored = DriverManager.getConnection(restoredUrl, "sa", "")) {
            RunScript.execute(restored, Files.newBufferedReader(backup));
            try (var statement = restored.createStatement()) {
                var user = statement.executeQuery("SELECT COUNT(*) FROM app_user WHERE username='recovery-user'");
                assertThat(user.next()).isTrue();
                assertThat(user.getInt(1)).isOne();
                var priority = statement.executeQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_name='VIDEO_TASKS' AND column_name='PROCESSING_PRIORITY'");
                assertThat(priority.next()).isTrue();
                assertThat(priority.getInt(1)).isZero();
            }
        } finally {
            Files.deleteIfExists(backup);
        }
    }

    private static TreeSet<String> schemaObjects(String url) throws Exception {
        var objects = new TreeSet<String>();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            var columns = statement.executeQuery("SELECT table_name,column_name,data_type,is_nullable FROM information_schema.columns WHERE table_schema='PUBLIC' AND table_name <> 'flyway_schema_history'");
            while (columns.next()) objects.add("C|" + columns.getString(1) + "|" + columns.getString(2) + "|" + columns.getString(3) + "|" + columns.getString(4));
            var indexes = statement.executeQuery("SELECT table_name,index_name FROM information_schema.indexes WHERE table_schema='PUBLIC' AND table_name <> 'flyway_schema_history' AND index_name LIKE 'IDX_%'");
            while (indexes.next()) objects.add("I|" + indexes.getString(1) + "|" + indexes.getString(2));
        }
        return objects;
    }

    @Test
    void latestMigrationAddsIndexedTaskStreamChangeTracking() throws Exception {
        String url = "jdbc:h2:mem:task-stream-tracking;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            var columns = statement.executeQuery("""
                    SELECT table_name,column_name FROM information_schema.columns
                    WHERE (table_name='VIDEO_TASKS' OR table_name='PROCESSING_STAGES')
                      AND column_name='UPDATED_AT'
                    """);
            int columnCount = 0;
            while (columns.next()) columnCount++;
            assertThat(columnCount).isEqualTo(2);
            var indexes = statement.executeQuery("""
                    SELECT index_name FROM information_schema.indexes
                    WHERE index_name IN ('IDX_VIDEO_TASKS_OWNER_UPDATED','IDX_PROCESSING_STAGES_TASK_UPDATED')
                    """);
            int indexCount = 0;
            while (indexes.next()) indexCount++;
            assertThat(indexCount).isEqualTo(2);
        }
    }

    @Test
    void latestMigrationAddsVideoTaskOptimisticLockVersion() throws Exception {
        String url = "jdbc:h2:mem:task-version;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var columns = statement.executeQuery("""
                     SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_name='VIDEO_TASKS' AND column_name='VERSION'
                     """)) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void latestMigrationsAddExplainableEventsNarrativePlanAndDirectorLearning() throws Exception {
        String url = "jdbc:h2:mem:event-timeline;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            var columns = statement.executeQuery("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_name='GAME_EVENTS'
                    AND column_name IN ('EVIDENCE_JSON','CONFIRMATION_STATUS','MANUALLY_EDITED',
                                        'KNOWLEDGE_PACK_CODE','ANCHOR_SECONDS')
                    """);
            int count = 0;
            while (columns.next()) count++;
            assertThat(count).isEqualTo(5);
            var constraints = statement.executeQuery("""
                    SELECT check_clause FROM information_schema.check_constraints
                    WHERE constraint_name='CK_GAME_EVENT_CONFIRMATION_STATUS'
                    """);
            assertThat(constraints.next()).isTrue();
            assertThat(constraints.getString(1)).contains("CONFIRMED").contains("NEEDS_REVIEW");
            var narrativeColumns = statement.executeQuery("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_name='BATTLE_NARRATIVE_PLAN'
                    AND column_name IN ('PLAN_JSON','CONFIRMED_EVENT_FINGERPRINT','APPLIED','GENERATED_AT','APPLIED_AT')
                    """);
            int narrativeCount = 0;
            while (narrativeColumns.next()) narrativeCount++;
            assertThat(narrativeCount).isEqualTo(5);
            var productTables = statement.executeQuery("""
                    SELECT table_name FROM information_schema.tables
                    WHERE table_name IN ('GAME_KNOWLEDGE_PACKS','DIRECTOR_EDIT_DECISIONS')
                    """);
            int productTableCount = 0;
            while (productTables.next()) productTableCount++;
            assertThat(productTableCount).isEqualTo(2);
            var sourceStorage = statement.executeQuery("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_name='SOURCE_MEDIA_STORAGE'
                    AND column_name IN ('STORAGE_MODE','STORAGE_PATH','SIZE_BYTES','FILESYSTEM_KEY','LAST_VERIFIED_AT')
                    """);
            int sourceStorageColumns = 0;
            while (sourceStorage.next()) sourceStorageColumns++;
            assertThat(sourceStorageColumns).isEqualTo(5);
            var ecosystemTables = statement.executeQuery("""
                    SELECT table_name FROM information_schema.tables
                    WHERE table_name IN ('COMMUNITY_RESOURCE','CREATIVE_VARIANT','EDITING_DECISION_REPORT')
                    """);
            int ecosystemTableCount = 0;
            while (ecosystemTables.next()) ecosystemTableCount++;
            assertThat(ecosystemTableCount).isEqualTo(3);
        }
    }

    @Test
    void v11RepairsBilibiliInterfaceTextAndRemovesDerivedTags() throws Exception {
        String url = "jdbc:h2:mem:bilibili-title-repair;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("10").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO external_asset(id,provider,external_id,asset_type,title,landing_url,license_code,
                      import_status,metadata_json,discovered_at)
                    VALUES(UUID '11111111-1111-1111-1111-111111111111','BILIBILI','dirty','VIDEO',
                      '添加至稍后再看28.5万52001:55','https://www.bilibili.com/video/BV1Ab411c7De',
                      'RIGHTS_REVIEW_REQUIRED','REFERENCE_ONLY','{}',CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO asset_tag(id,normalized_name,display_name,created_at)
                    VALUES(UUID '22222222-2222-2222-2222-222222222222','bad','动作：添加至稍后再看',CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO asset_tag_assignment(id,asset_id,tag_id,tag_source,confidence,created_at)
                    VALUES(UUID '33333333-3333-3333-3333-333333333333',
                      UUID '11111111-1111-1111-1111-111111111111',UUID '22222222-2222-2222-2222-222222222222',
                      'AI',0.65,CURRENT_TIMESTAMP)
                    """);
        }

        Flyway.configure().dataSource(url, "sa", "").load().migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            var title = statement.executeQuery("SELECT title,localized_title FROM external_asset WHERE external_id='dirty'");
            assertThat(title.next()).isTrue();
            assertThat(title.getString("title")).isEqualTo("Bilibili 视频 BV1Ab411c7De");
            assertThat(title.getString("localized_title")).isNull();
            var assignments = statement.executeQuery("SELECT COUNT(*) FROM asset_tag_assignment");
            assertThat(assignments.next()).isTrue();
            assertThat(assignments.getInt(1)).isZero();
        }
    }

    @Test
    void v10ReplacesLegacyTaskStatusConstraintWithStoryboardReviewAwareConstraint() throws Exception {
        String url = "jdbc:h2:mem:legacy-task-status;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("9").load().migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    ALTER TABLE video_tasks ADD CONSTRAINT CONSTRAINT_98
                    CHECK (status IN ('DRAFT', 'READY', 'PROCESSING', 'COMPLETED', 'FAILED'))
                    """);
        }

        Flyway.configure().dataSource(url, "sa", "").load().migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            var constraints = statement.executeQuery("""
                    SELECT constraint_name, check_clause
                    FROM information_schema.check_constraints
                    WHERE constraint_name IN ('CONSTRAINT_98', 'CK_VIDEO_TASKS_STATUS')
                    ORDER BY constraint_name
                    """);
            assertThat(constraints.next()).isTrue();
            assertThat(constraints.getString("constraint_name")).isEqualTo("CK_VIDEO_TASKS_STATUS");
            assertThat(constraints.getString("check_clause")).contains("WAITING_REVIEW");
            assertThat(constraints.next()).isFalse();
        }
    }

    @Test
    void v38AddsPersistentProcessingPriorityAndQueueIndex() throws Exception {
        String url = "jdbc:h2:mem:task-priority;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            var column = statement.executeQuery("""
                    SELECT column_default FROM information_schema.columns
                    WHERE table_name='VIDEO_TASKS' AND column_name='PROCESSING_PRIORITY'
                    """);
            assertThat(column.next()).isTrue();
            assertThat(column.getString(1)).contains("0");
            var index = statement.executeQuery("""
                    SELECT COUNT(*) FROM information_schema.indexes
                    WHERE table_name='VIDEO_TASKS' AND index_name='IDX_VIDEO_TASKS_PROCESSING_PRIORITY'
                    """);
            assertThat(index.next()).isTrue();
            assertThat(index.getInt(1)).isEqualTo(1);
        }
    }
}
