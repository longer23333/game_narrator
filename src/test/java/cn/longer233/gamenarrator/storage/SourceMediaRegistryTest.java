package cn.longer233.gamenarrator.storage;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SourceMediaRegistryTest {
    @TempDir Path temporary;

    @Test
    void recordsManagedSourceWithoutPuttingVideoBytesInDatabase() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:source-storage;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID taskId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO video_tasks(id,name,game_category,commentary_style,target_duration_seconds,
                task_brief,source_video_path,status,created_at) VALUES(?,?,?,?,?,?,?,?,?)
                """, taskId, "large", "ACTION", "ANIME_THEATER", 60, "test", "source.mp4", "READY", OffsetDateTime.now());
        Path source = temporary.resolve("large.mp4");
        Files.write(source, new byte[4096]);

        new SourceMediaRegistry(jdbc).registerManaged(taskId, source, "large.mp4", "video/mp4");

        var row = jdbc.queryForMap("SELECT storage_mode,storage_path,size_bytes FROM source_media_storage WHERE task_id=?", taskId);
        assertThat(row.get("STORAGE_MODE")).isEqualTo("MANAGED");
        assertThat(((Number) row.get("SIZE_BYTES")).longValue()).isEqualTo(4096);
        assertThat(row.get("STORAGE_PATH").toString()).endsWith("large.mp4");
    }
}
