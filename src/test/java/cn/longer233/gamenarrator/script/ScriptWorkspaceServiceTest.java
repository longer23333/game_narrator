package cn.longer233.gamenarrator.script;

import cn.longer233.gamenarrator.highlight.HighlightClip;
import cn.longer233.gamenarrator.task.domain.CommentaryStyle;
import cn.longer233.gamenarrator.task.domain.ProcessingStageType;
import cn.longer233.gamenarrator.task.domain.StageStatus;
import cn.longer233.gamenarrator.task.domain.TaskStatus;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import cn.longer233.gamenarrator.pipeline.TaskArtifactLocator;
import cn.longer233.gamenarrator.voice.VoiceSynthesizer;
import cn.longer233.gamenarrator.voice.VoiceSegment;
import cn.longer233.gamenarrator.timeline.TimelinePlanner;
import cn.longer233.gamenarrator.timeline.TimelinePlanningResult;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
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
        ScriptWorkspaceService service = service(repository, mapper,
                mock(TextGenerator.class), mock(VoiceSynthesizer.class), task, null);

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
    void editingOneSegmentRegeneratesOnlyItsVoiceAndRefreshesExistingTimeline() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path script = temporaryDirectory.resolve("local-script.json");
        Path highlight = temporaryDirectory.resolve("local-highlights.json");
        Path manifest = temporaryDirectory.resolve("voice-manifest.json");
        Path timeline = temporaryDirectory.resolve("timeline.json");
        Path firstVoice = java.nio.file.Files.write(temporaryDirectory.resolve("first.wav"), new byte[]{1});
        Path secondVoice = java.nio.file.Files.write(temporaryDirectory.resolve("second.wav"), new byte[]{2});
        List<ScriptSegment> segments = List.of(
                new ScriptSegment(1, 0, 5, "first", "s1", "e1"),
                new ScriptSegment(2, 5, 10, "old", "s2", "e2"));
        mapper.writeValue(script.toFile(), Map.of("title", "title", "synopsis", "synopsis",
                "fullNarration", "first\nold", "segments", segments));
        mapper.writeValue(highlight.toFile(), Map.of("clips", List.of(
                new HighlightClip(1, 0, 5, 2, "A", "a", 50, 50),
                new HighlightClip(2, 5, 10, 7, "B", "b", 60, 60))));
        mapper.writeValue(manifest.toFile(), Map.of("segments", List.of(
                new VoiceSegment(1, firstVoice.toString(), "first"),
                new VoiceSegment(2, secondVoice.toString(), "old"))));
        mapper.writeValue(timeline.toFile(), Map.of("segments", List.of(), "outputDurationSeconds", 10));
        VideoTask task = new VideoTask("demo", "ACTION", CommentaryStyle.ANIME_THEATER,
                30, "brief", temporaryDirectory.resolve("source.mp4").toString());
        task.completeHighlightSelection("highlights", highlight.toString(), 2);
        task.completeScriptGeneration("title", "synopsis", "first\nold", script.toString(), 2);
        task.completeVoiceGeneration(manifest.toString(), 2);
        task.completeTimelinePlanning(timeline.toString(), 10, 0);
        VideoTaskRepository repository = mock(VideoTaskRepository.class);
        when(repository.findById(task.getId())).thenReturn(Optional.of(task));
        VoiceSynthesizer voice = mock(VoiceSynthesizer.class);
        doAnswer(invocation -> {
            VoiceSegment revisedVoice = new VoiceSegment(2, secondVoice.toString(), "revised");
            mapper.writeValue(manifest.toFile(), Map.of("segments", List.of(
                    new VoiceSegment(1, firstVoice.toString(), "first"), revisedVoice)));
            return revisedVoice;
        }).when(voice).regenerateSegment(script, 2, (cn.longer233.gamenarrator.voice.VoiceRegenerationRequest) null);
        TimelinePlanner planner = mock(TimelinePlanner.class);
        when(planner.refreshSegment(timeline, highlight, script, manifest, 2)).thenReturn(
                new TimelinePlanningResult(timeline.toString(), 10, 0, List.of()));
        ScriptWorkspaceService service = service(repository, mapper,
                mock(TextGenerator.class), voice, task, planner);

        service.update(task.getId(), 2, new UpdateScriptSegmentRequest("revised", "new subtitle", "e2"));

        verify(voice).regenerateSegment(script, 2, (cn.longer233.gamenarrator.voice.VoiceRegenerationRequest) null);
        verify(voice, never()).regenerateSegment(script, 1,
                (cn.longer233.gamenarrator.voice.VoiceRegenerationRequest) null);
        verify(planner).refreshSegment(timeline, highlight, script, manifest, 2);
        assertThat(stage(task, ProcessingStageType.VOICE_GENERATION)).isEqualTo(StageStatus.COMPLETED);
        assertThat(stage(task, ProcessingStageType.TIMELINE_PLANNING)).isEqualTo(StageStatus.COMPLETED);
        assertThat(stage(task, ProcessingStageType.RENDERING)).isEqualTo(StageStatus.PENDING);
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
        ScriptWorkspaceService service = service(repository, mapper,
                mock(TextGenerator.class), mock(VoiceSynthesizer.class), task, null);

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
        ScriptWorkspaceService service = service(repository, mapper,
                mock(TextGenerator.class), mock(VoiceSynthesizer.class), task, null);

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
        ScriptWorkspaceService service = service(repository, mapper,
                generator, mock(VoiceSynthesizer.class), task, null);

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
        ScriptWorkspaceService service = service(repository, mapper,
                generator, mock(VoiceSynthesizer.class), task, null);

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
        ScriptWorkspaceService service = service(repository, mapper,
                mock(TextGenerator.class), mock(VoiceSynthesizer.class), task, null);

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

    private ScriptWorkspaceService service(VideoTaskRepository repository, ObjectMapper mapper,
                                           TextGenerator generator, VoiceSynthesizer voice,
                                           VideoTask task, TimelinePlanner planner) {
        TaskArtifactLocator locator = mock(TaskArtifactLocator.class);
        artifact(locator, task, "HIGHLIGHT_MANIFEST", "highlight");
        artifact(locator, task, "SCRIPT_MANIFEST", "script", "review", "rewrite");
        artifact(locator, task, "VOICE_MANIFEST", "voice-manifest");
        artifact(locator, task, "TIMELINE_MANIFEST", "timeline");
        return new ScriptWorkspaceService(repository, mapper, generator, voice, null, planner, locator);
    }

    private void artifact(TaskArtifactLocator locator, VideoTask task, String type, String... nameParts) {
        try (var files = java.nio.file.Files.list(temporaryDirectory)) {
            Optional<Path> path = files.filter(java.nio.file.Files::isRegularFile)
                    .filter(file -> java.util.Arrays.stream(nameParts)
                            .anyMatch(part -> file.getFileName().toString().contains(part)))
                    .findFirst();
            when(locator.latest(task.getId(), type)).thenReturn(path);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
