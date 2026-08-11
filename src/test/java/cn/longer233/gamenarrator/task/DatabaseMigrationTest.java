package cn.longer233.gamenarrator.task;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationTest {
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
