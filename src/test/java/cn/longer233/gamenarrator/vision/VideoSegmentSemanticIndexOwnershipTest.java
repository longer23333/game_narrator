package cn.longer233.gamenarrator.vision;

import cn.longer233.gamenarrator.asset.BgeAssetSemanticSearch;
import cn.longer233.gamenarrator.identity.CurrentUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VideoSegmentSemanticIndexOwnershipTest {
    @TempDir Path storage;

    @Test
    void searchAndThumbnailExcludeTasksInTrash() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID active = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        JdbcTemplate jdbc = database();
        insertTask(jdbc, active, owner, null, "当前项目");
        insertTask(jdbc, deleted, owner, Timestamp.from(Instant.now()), "回收站项目");
        insertEmbedding(jdbc, active, 1, 11L);
        insertEmbedding(jdbc, deleted, 2, 12L);

        BgeAssetSemanticSearch embeddings = mock(BgeAssetSemanticSearch.class);
        when(embeddings.model()).thenReturn("test-model");
        when(embeddings.embedTexts(anyList())).thenReturn(List.of(new double[]{1}));
        when(embeddings.similarity(org.mockito.ArgumentMatchers.any(double[].class),
                org.mockito.ArgumentMatchers.any(double[].class))).thenReturn(.8);
        CurrentUserContext current = mock(CurrentUserContext.class);
        when(current.userId()).thenReturn(owner);
        var index = new VideoSegmentSemanticIndex(jdbc, new ObjectMapper(), embeddings,
                storage.toString(), current);

        assertThat(index.search("战斗", 10)).extracting(VideoSegmentSearchResult::taskId)
                .containsExactly(active);
        assertThat(index.searchByImage(png(), 10)).extracting(VideoSegmentSearchResult::taskId)
                .containsExactly(active);
        assertThatThrownBy(() -> index.thumbnail(deleted, 2))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("镜头不存在");
    }

    private JdbcTemplate database() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE video_tasks(id UUID PRIMARY KEY,name VARCHAR,owner_id UUID,deleted_at TIMESTAMP,project_id UUID)");
        jdbc.execute("CREATE TABLE artifact(project_id UUID,artifact_type VARCHAR,deleted_at TIMESTAMP,created_at TIMESTAMP,storage_key VARCHAR)");
        jdbc.execute("CREATE TABLE video_segment_embedding(task_id UUID,frame_index INT,timestamp_seconds DOUBLE,event_type VARCHAR,description VARCHAR,image_path VARCHAR,image_hash BIGINT,model VARCHAR,vector_json VARCHAR)");
        return jdbc;
    }

    private void insertTask(JdbcTemplate jdbc, UUID id, UUID owner, Timestamp deletedAt, String name) {
        jdbc.update("INSERT INTO video_tasks VALUES(?,?,?,?,?)", id, name, owner, deletedAt, UUID.randomUUID());
    }

    private void insertEmbedding(JdbcTemplate jdbc, UUID task, int frame, long imageHash) {
        jdbc.update("INSERT INTO video_segment_embedding VALUES(?,?,?,?,?,?,?,?,?)", task, frame,
                frame * 10d, "BATTLE", "战斗画面", storage.resolve(frame + ".jpg").toString(),
                imageHash, "test-model", "[1.0]");
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
