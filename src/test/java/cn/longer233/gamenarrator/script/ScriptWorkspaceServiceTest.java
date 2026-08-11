package cn.longer233.gamenarrator.script;

import cn.longer233.gamenarrator.highlight.HighlightClip;
import cn.longer233.gamenarrator.task.domain.CommentaryStyle;
import cn.longer233.gamenarrator.task.domain.ProcessingStageType;
import cn.longer233.gamenarrator.task.domain.StageStatus;
import cn.longer233.gamenarrator.task.domain.TaskStatus;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import cn.longer233.gamenarrator.voice.VoiceSynthesizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                repository, mapper, mock(TextGenerator.class), mock(VoiceSynthesizer.class));

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

    @Test
    void storyboardDecisionKeepsHiddenCandidateLedgerAndExcludesDuration() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path scriptPath = temporaryDirectory.resolve("decision-script.json");
        Path highlightPath = temporaryDirectory.resolve("decision-highlights.json");
        ScriptSegment segment = new ScriptSegment(1, 0, 10, "text", "subtitle", "cut");
        HighlightClip visible = new HighlightClip(1, 0, 10, 5,
                "ACTION", "visible", 80, 90, false, false);
        HighlightClip hiddenDecision = new HighlightClip(99, 40, 50, 45,
                "ACTION", "hidden", 70, 80, false, true);
        mapper.writeValue(scriptPath.toFile(), Map.of("title", "title", "synopsis", "synopsis",
                "fullNarration", "text", "segments", List.of(segment)));
        mapper.writeValue(highlightPath.toFile(), Map.of("clips", List.of(visible),
                "manualDecisions", List.of(hiddenDecision), "manualDecisionCount", 1,
                "selectedDurationSeconds", 10));
        VideoTask task = new VideoTask("demo", "ACTION", CommentaryStyle.ANIME_THEATER,
                30, "brief", temporaryDirectory.resolve("source.mp4").toString());
        task.completeHighlightSelection("one", highlightPath.toString(), 1);
        task.completeScriptGeneration("title", "synopsis", "text", scriptPath.toString(), 1);
        VideoTaskRepository repository = mock(VideoTaskRepository.class);
        when(repository.findById(task.getId())).thenReturn(Optional.of(task));
        ScriptWorkspaceService service = new ScriptWorkspaceService(repository, mapper,
                mock(TextGenerator.class), mock(VoiceSynthesizer.class));

        service.updateStoryboard(task.getId(), 1,
                new UpdateStoryboardSegmentRequest(0, 10, "text", "subtitle", "cut", false, true));

        JsonNode stored = mapper.readTree(highlightPath.toFile());
        assertThat(stored.path("manualDecisions")).hasSize(2);
        assertThat(stored.path("manualDecisions").toString()).contains("\"sourceFrameIndex\":99");
        assertThat(stored.path("manualDecisionCount").asInt()).isEqualTo(2);
        assertThat(stored.path("selectedDurationSeconds").asDouble()).isZero();
    }

    @Test
    void storesManualReviewPerSegmentWithoutReplacingAiReview() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path scriptPath = temporaryDirectory.resolve("review-script.json");
        List<ScriptSegment> segments = List.of(new ScriptSegment(1, 0, 8, "text", "subtitle", "cut"));
        mapper.writeValue(scriptPath.toFile(), Map.of("title", "title", "synopsis", "synopsis",
                "fullNarration", "text", "qualityReview", Map.of("score", 60, "passed", false,
                        "issues", List.of("事实需要核对"), "summary", "需复核"), "segments", segments));
        VideoTask task = new VideoTask("demo", "ACTION", CommentaryStyle.ANIME_THEATER,
                30, "brief", temporaryDirectory.resolve("source.mp4").toString());
        task.completeScriptGeneration("title", "synopsis", "text", scriptPath.toString(), 1);
        VideoTaskRepository repository = mock(VideoTaskRepository.class);
        when(repository.findById(task.getId())).thenReturn(Optional.of(task));
        ScriptWorkspaceService service = new ScriptWorkspaceService(repository, mapper,
                mock(TextGenerator.class), mock(VoiceSynthesizer.class));

        service.review(task.getId(), 1, new ManualScriptReviewRequest("NEEDS_CHANGES", "角色名称不准确"));

        service.update(task.getId(), 1, new UpdateScriptSegmentRequest("new text", "new subtitle", "cut"));

        assertThat(service.reviews(task.getId()).toString()).contains("NEEDS_CHANGES", "角色名称不准确");
        JsonNode revised = mapper.readTree(scriptPath.toFile());
        assertThat(revised.path("qualityReview").path("score").asInt()).isZero();
        assertThat(revised.path("qualityReview").path("summary").asText()).contains("重新进行 AI");
    }

    @Test
    void independentReviewPersistsModelFeedbackWithoutDiscardingManualReviews() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path scriptPath = temporaryDirectory.resolve("quality-review.json");
        List<ScriptSegment> segments = List.of(new ScriptSegment(1, 0, 8, "text", "subtitle", "cut"));
        mapper.writeValue(scriptPath.toFile(), Map.of("title", "title", "synopsis", "synopsis",
                "fullNarration", "text", "manualReviews", Map.of("1", Map.of(
                        "status", "NEEDS_CHANGES", "note", "事实不准确")), "segments", segments));
        VideoTask task = new VideoTask("demo", "ACTION", CommentaryStyle.ANIME_THEATER,
                30, "brief", temporaryDirectory.resolve("source.mp4").toString());
        task.completeScriptGeneration("title", "synopsis", "text", scriptPath.toString(), 1);
        VideoTaskRepository repository = mock(VideoTaskRepository.class);
        when(repository.findById(task.getId())).thenReturn(Optional.of(task));
        TextGenerator generator = mock(TextGenerator.class);
        when(generator.reviewQuality(any(), anyMap())).thenReturn(new ScriptQualityReview(
                58, false, List.of("片段 1：事实不准确，请核对"), "需要修改"));
        ScriptWorkspaceService service = new ScriptWorkspaceService(repository, mapper,
                generator, mock(VoiceSynthesizer.class));

        ScriptDocumentView result = service.qualityReview(task.getId());

        assertThat(result.qualityReview().score()).isEqualTo(58);
        JsonNode stored = mapper.readTree(scriptPath.toFile());
        assertThat(stored.path("qualityReviewSource").asText()).isEqualTo("AI_INDEPENDENT_REVIEW");
        assertThat(stored.path("manualReviews").path("1").path("note").asText()).isEqualTo("事实不准确");
        verify(generator).reviewQuality(any(), anyMap());
    }

    @Test
    void segmentRegenerationUsesManualFeedbackWhenInstructionIsBlank() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path scriptPath = temporaryDirectory.resolve("feedback-rewrite.json");
        Path highlightPath = temporaryDirectory.resolve("highlights.json");
        ScriptSegment segment = new ScriptSegment(1, 0, 8, "old", "old subtitle", "cut");
        mapper.writeValue(scriptPath.toFile(), Map.of("title", "title", "synopsis", "synopsis",
                "fullNarration", "old", "qualityReview", Map.of("score", 55, "passed", false,
                        "issues", List.of("片段 1：语气拖沓，请精简"), "summary", "需修改"),
                "manualReviews", Map.of("1", Map.of("status", "NEEDS_CHANGES", "note", "角色名称不准确")),
                "segments", List.of(segment)));
        mapper.writeValue(highlightPath.toFile(), Map.of("clips", List.of(Map.of(
                "sourceFrameIndex", 1, "startSeconds", 0, "endSeconds", 8, "anchorSeconds", 4,
                "eventType", "ACTION", "description", "scene", "sourceScore", 80, "finalScore", 90))));
        VideoTask task = new VideoTask("demo", "ACTION", CommentaryStyle.ANIME_THEATER,
                30, "brief", temporaryDirectory.resolve("source.mp4").toString());
        task.completeHighlightSelection("one", highlightPath.toString(), 1);
        task.completeScriptGeneration("title", "synopsis", "old", scriptPath.toString(), 1);
        VideoTaskRepository repository = mock(VideoTaskRepository.class);
        when(repository.findById(task.getId())).thenReturn(Optional.of(task));
        TextGenerator generator = mock(TextGenerator.class);
        when(generator.regenerateSegment(any(), any(), any(), any())).thenReturn(
                new ScriptSegment(1, 0, 8, "new", "new subtitle", "cut"));
        ScriptWorkspaceService service = new ScriptWorkspaceService(repository, mapper,
                generator, mock(VoiceSynthesizer.class));

        service.regenerate(task.getId(), 1, new RegenerateScriptSegmentRequest(""));

        verify(generator).regenerateSegment(eq(segment), contains("人工评审：角色名称不准确"),
                eq(null), eq(null));
    }

    @Test
    void rejectsManualReviewForUnknownSegmentWithoutChangingDocument() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path scriptPath = temporaryDirectory.resolve("missing-segment-review.json");
        mapper.writeValue(scriptPath.toFile(), Map.of("title", "title", "synopsis", "synopsis",
                "fullNarration", "text", "segments",
                List.of(new ScriptSegment(1, 0, 8, "text", "subtitle", "cut"))));
        VideoTask task = new VideoTask("demo", "ACTION", CommentaryStyle.ANIME_THEATER,
                30, "brief", temporaryDirectory.resolve("source.mp4").toString());
        task.completeScriptGeneration("title", "synopsis", "text", scriptPath.toString(), 1);
        VideoTaskRepository repository = mock(VideoTaskRepository.class);
        when(repository.findById(task.getId())).thenReturn(Optional.of(task));
        ScriptWorkspaceService service = new ScriptWorkspaceService(repository, mapper,
                mock(TextGenerator.class), mock(VoiceSynthesizer.class));

        assertThatThrownBy(() -> service.review(task.getId(), 99,
                new ManualScriptReviewRequest("APPROVED", "not present")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(mapper.readTree(scriptPath.toFile()).has("manualReviews")).isFalse();
    }

    private StageStatus stage(VideoTask task, ProcessingStageType type) {
        return task.getStages().stream()
                .filter(item -> item.getStageType() == type)
                .findFirst().orElseThrow().getStatus();
    }
}
