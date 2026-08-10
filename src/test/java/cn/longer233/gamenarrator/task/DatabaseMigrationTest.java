package cn.longer233.gamenarrator.task;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationTest {
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
    void latestMigrationAddsExplainableGameEventFields() throws Exception {
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
}
