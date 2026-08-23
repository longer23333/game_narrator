package cn.longer233.gamenarrator.editor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import java.util.Map;

class EditorTrackOperationsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final EditorTimelineService service = new EditorTimelineService(null, mapper, null,
            null, null, mock(AudioWaveformCache.class), null);

    @Test
    void addsReordersAndDeletesAnEmptyManagedTrack() {
        ObjectNode timeline = timeline();

        service.addManagedTrack(timeline, "audio", "环境音");
        ArrayNode tracks = timeline.withArray("tracks");
        String addedId = tracks.get(2).path("id").asText();
        assertThat(tracks.get(2).path("type").asText()).isEqualTo("AUDIO");

        service.moveTrack(timeline, addedId, "UP");
        assertThat(tracks.get(1).path("id").asText()).isEqualTo(addedId);
        assertThat(tracks).extracting(node -> node.path("order").asInt()).containsExactly(0, 1, 2);

        service.deleteTrack(timeline, addedId);
        assertThat(tracks).extracting(node -> node.path("id").asText())
                .containsExactly("video-1", "audio-1");
    }

    @Test
    void protectsMainAndOccupiedTracksAndRejectsVideoTrackCreation() {
        ObjectNode timeline = timeline();
        timeline.withArray("clips").addObject().put("id", "clip-1").put("trackId", "audio-1");

        assertThatThrownBy(() -> service.deleteTrack(timeline, "video-1"))
                .hasMessageContaining("主视频轨道不能删除");
        assertThatThrownBy(() -> service.deleteTrack(timeline, "audio-1"))
                .hasMessageContaining("先移走轨道中的片段");
        assertThatThrownBy(() -> service.addManagedTrack(timeline, "VIDEO", "额外视频"))
                .hasMessageContaining("只能新增叠加、音频或字幕轨道");
    }

    @Test
    void rejectsNonFiniteTimelineNumbers() {
        assertThatThrownBy(() -> EditorTimelineService.number(Map.of("value","NaN"),"value"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("有限数字");
        assertThatThrownBy(() -> EditorTimelineService.number(Map.of("value",Double.POSITIVE_INFINITY),"value"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("有限数字");
    }

    private ObjectNode timeline() {
        ObjectNode timeline = mapper.createObjectNode();
        ArrayNode tracks = timeline.putArray("tracks");
        tracks.addObject().put("id", "video-1").put("type", "VIDEO").put("name", "主视频")
                .put("order", 0).put("muted", false).put("solo", false).put("locked", false);
        tracks.addObject().put("id", "audio-1").put("type", "AUDIO").put("name", "原声")
                .put("order", 1).put("muted", false).put("solo", false).put("locked", false);
        timeline.putArray("clips");
        return timeline;
    }
}
