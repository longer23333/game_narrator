package cn.longer233.gamenarrator.script;

import cn.longer233.gamenarrator.task.domain.CommentaryStyle;
import cn.longer233.gamenarrator.task.domain.ProcessingStageType;
import cn.longer233.gamenarrator.task.domain.StageStatus;
import cn.longer233.gamenarrator.task.domain.TaskStatus;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import cn.longer233.gamenarrator.voice.VoiceGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScriptWorkspaceServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void editingOneSegmentPreservesOthersAndInvalidatesOnlyDownstreamStages() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path scriptPath = temporaryDirectory.resolve("generated-script.json");
        List<ScriptSegment> segments = List.of(
                new ScriptSegment(1, 0, 10, "first", "first subtitle", "cut"),
                new ScriptSegment(2, 10, 20, "second", "second subtitle", "zoom"));
        mapper.writeValue(scriptPath.toFile(), Map.of(
                "model", "test-model",
                "title", "title",
                "synopsis", "synopsis",
                "fullNarration", "first\nsecond",
                "segments", segments));

        VideoTask task = new VideoTask("demo", "ACTION", CommentaryStyle.ANIME_THEATER,
                30, "brief", temporaryDirectory.resolve("source.mp4").toString());
        task.completeScriptGeneration("title", "synopsis", "first\nsecond",
                scriptPath.toString(), 2);
        VideoTaskRepository repository = mock(VideoTaskRepository.class);
        when(repository.findById(task.getId())).thenReturn(Optional.of(task));
        ScriptWorkspaceService service = new ScriptWorkspaceService(
                repository, mapper, mock(OllamaScriptGenerator.class), mock(VoiceGenerator.class));

        ScriptDocumentView result = service.update(task.getId(), 2,
                new UpdateScriptSegmentRequest("revised", "new subtitle", "shake"));

        assertThat(result.segments()).extracting(ScriptSegment::narration)
                .containsExactly("first", "revised");
        assertThat(task.getGeneratedNarration()).isEqualTo("first\nrevised");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.READY);
        assertThat(stage(task, ProcessingStageType.SCRIPT_GENERATION)).isEqualTo(StageStatus.COMPLETED);
        assertThat(stage(task, ProcessingStageType.VOICE_GENERATION)).isEqualTo(StageStatus.PENDING);
        assertThat(stage(task, ProcessingStageType.TIMELINE_PLANNING)).isEqualTo(StageStatus.PENDING);
        assertThat(stage(task, ProcessingStageType.RENDERING)).isEqualTo(StageStatus.PENDING);
        assertThat(mapper.readTree(scriptPath.toFile()).path("segments").get(1)
                .path("narration").asText()).isEqualTo("revised");
    }

    private StageStatus stage(VideoTask task, ProcessingStageType type) {
        return task.getStages().stream()
                .filter(item -> item.getStageType() == type)
                .findFirst().orElseThrow().getStatus();
    }
}
