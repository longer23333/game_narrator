package cn.longer233.gamenarrator.export;

import cn.longer233.gamenarrator.identity.CurrentUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.longer233.gamenarrator.common.OwnedResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import cn.longer233.gamenarrator.pipeline.TaskArtifactLocator;
import static org.mockito.Mockito.when;

class ExportOwnershipTest {
    @TempDir Path root;

    @Test
    void directExportIdsCannotCrossUserBoundaries() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:export-owner;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE export_preset(id UUID PRIMARY KEY,name VARCHAR,container VARCHAR)");
        jdbc.execute("CREATE TABLE artifact(id UUID PRIMARY KEY,storage_key VARCHAR,size_bytes BIGINT)");
        jdbc.execute("CREATE TABLE export_job(id UUID PRIMARY KEY,project_id UUID,requested_by UUID,preset_id UUID,export_name VARCHAR,status VARCHAR,progress INT,output_artifact_id UUID,error_message VARCHAR,download_count INT,created_at TIMESTAMP WITH TIME ZONE,completed_at TIMESTAMP WITH TIME ZONE,downloaded_at TIMESTAMP WITH TIME ZONE)");
        UUID owner = UUID.randomUUID(), intruder = UUID.randomUUID(), preset = UUID.randomUUID();
        UUID artifact = UUID.randomUUID(), job = UUID.randomUUID();
        Path output = Files.writeString(root.resolve("export.mp4"), "video");
        jdbc.update("INSERT INTO export_preset VALUES(?,?,?)", preset, "Web", "mp4");
        jdbc.update("INSERT INTO artifact VALUES(?,?,?)", artifact, output.toString(), Files.size(output));
        jdbc.update("INSERT INTO export_job VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", job, UUID.randomUUID(), owner,
                preset, "owned", "COMPLETED", 100, artifact, null, 0, OffsetDateTime.now(), OffsetDateTime.now(), null);
        CurrentUserContext current = mock(CurrentUserContext.class);
        when(current.userId()).thenReturn(owner);
        ExportService service = new ExportService(jdbc, new ObjectMapper(), mock(ExportWorker.class), current,
                mock(TaskArtifactLocator.class));

        assertThat(service.findJob(job).id()).isEqualTo(job);
        assertThat(service.download(job).filename()).isEqualTo("owned.mp4");

        when(current.userId()).thenReturn(intruder);
        assertThatThrownBy(() -> service.findJob(job)).isInstanceOf(OwnedResourceNotFoundException.class);
        assertThatThrownBy(() -> service.download(job)).isInstanceOf(OwnedResourceNotFoundException.class);
    }
}
