package cn.longer233.gamenarrator.quality;

import cn.longer233.gamenarrator.event.GameEventEvidence;
import cn.longer233.gamenarrator.event.GameEventView;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.longer233.gamenarrator.event.GameEventTimelineService;
import cn.longer233.gamenarrator.script.ScriptWorkspaceService;
import cn.longer233.gamenarrator.script.StoryboardSegmentView;
import cn.longer233.gamenarrator.script.StoryboardView;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NarrativeConsistencyServiceTest {
    @Test
    void blocksPassingScoreWhenUpstreamEvidenceCoverageIsLow() throws Exception {
        UUID taskId = UUID.randomUUID();
        var events = mock(GameEventTimelineService.class);
        var workspace = mock(ScriptWorkspaceService.class);
        var tasks = mock(VideoTaskRepository.class);
        var task = mock(VideoTask.class);
        var visual = Files.createTempFile("visual-evidence", ".json");
        var evidenceDir = Files.createTempDirectory("narrative-evidence");
        var transcript = evidenceDir.resolve("transcript.json");
        var speakers = evidenceDir.resolve("speaker-segments.json");
        Files.writeString(speakers, """
                {"segments":[{"startMillis":0,"endMillis":5000,"speakerType":"PLAYER_VOICE","confidence":1.0,"evidence":"NATIVE_LABEL"}]}
                """);
        Files.writeString(transcript, "{}");
        when(task.getVisualAnalysisPath()).thenReturn(visual.toString());
        when(task.getTranscriptJsonPath()).thenReturn(transcript.toString());
        when(task.getTranscriptTextPath()).thenReturn(null);
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));
        when(events.list(taskId)).thenReturn(List.of(new GameEventView(UUID.randomUUID(), taskId, 0, 5, 2,
                "BATTLE", .55, 80, "confirmed", List.of(new GameEventEvidence("OCR", "victory", 2, 1)),
                "CONFIRMED", false, "boss-battle", OffsetDateTime.now())));
        when(workspace.storyboard(taskId)).thenReturn(new StoryboardView("title", "synopsis", true, true,
                List.of(segment(1, 0, 5), segment(2, 10, 15), segment(3, 20, 25))));

        NarrativeQualityReport report = new NarrativeConsistencyService(events, workspace, tasks, new ObjectMapper()).inspect(taskId);

        assertThat(report.passed()).isFalse();
        assertThat(report.supportedSegmentCount()).isEqualTo(1);
        assertThat(report.evidenceCoverage()).isCloseTo(1d / 3d, org.assertj.core.data.Offset.offset(.001));
        assertThat(report.issues()).extracting(NarrativeQualityReport.Issue::type)
                .contains("EVIDENCE_COVERAGE", "EVENT_CONFIDENCE");
        assertThat(report.ocrEvidenceCoverage()).isEqualTo(1);
        assertThat(report.knowledgeEvidenceCoverage()).isEqualTo(1);
        assertThat(report.speakerCoverage()).isCloseTo(1d / 3d, org.assertj.core.data.Offset.offset(.001));
        assertThat(report.upstreamEvidenceReliability()).isLessThan(.75);
        assertThat(report.issues()).extracting(NarrativeQualityReport.Issue::type).contains("SPEAKER_EVIDENCE");
    }

    @Test
    void lowConfidenceFallbackAndWeakCrossEvidenceCapOtherwiseCleanScore() throws Exception {
        UUID taskId = UUID.randomUUID();
        var events = mock(GameEventTimelineService.class);
        var workspace = mock(ScriptWorkspaceService.class);
        var tasks = mock(VideoTaskRepository.class);
        var task = mock(VideoTask.class);
        var evidenceDir = Files.createTempDirectory("weak-narrative-evidence");
        var visual = evidenceDir.resolve("vision.json");
        var transcript = evidenceDir.resolve("transcript.json");
        Files.writeString(visual, "{}");
        Files.writeString(transcript, "{}");
        Files.writeString(evidenceDir.resolve("speaker-segments.json"), """
                {"segments":[{"startMillis":0,"endMillis":5000,"speakerType":"PLAYER_VOICE","confidence":0.4,"evidence":"TEXT_FALLBACK"}]}
                """);
        when(task.getVisualAnalysisPath()).thenReturn(visual.toString());
        when(task.getTranscriptJsonPath()).thenReturn(transcript.toString());
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));
        when(events.list(taskId)).thenReturn(List.of(new GameEventView(UUID.randomUUID(), taskId, 0, 5, 2,
                "BATTLE", .8, 80, "confirmed", List.of(), "CONFIRMED", false, null, OffsetDateTime.now())));
        when(workspace.storyboard(taskId)).thenReturn(new StoryboardView("title", "synopsis", true, true,
                List.of(segment(1, 0, 5))));

        NarrativeQualityReport report = new NarrativeConsistencyService(events, workspace, tasks, new ObjectMapper()).inspect(taskId);

        assertThat(report.evidenceCoverage()).isEqualTo(1);
        assertThat(report.speakerCoverage()).isCloseTo(.24, org.assertj.core.data.Offset.offset(.001));
        assertThat(report.upstreamEvidenceReliability()).isCloseTo(.368, org.assertj.core.data.Offset.offset(.001));
        assertThat(report.score()).isEqualTo(37);
        assertThat(report.passed()).isFalse();
    }

    @Test
    void silentVideoDoesNotReceiveArtificialSpeakerPenalty() throws Exception {
        UUID taskId = UUID.randomUUID();
        var events = mock(GameEventTimelineService.class);
        var workspace = mock(ScriptWorkspaceService.class);
        var tasks = mock(VideoTaskRepository.class);
        var task = mock(VideoTask.class);
        var visual = Files.createTempFile("silent-video-vision", ".json");
        when(task.getVisualAnalysisPath()).thenReturn(visual.toString());
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));
        when(events.list(taskId)).thenReturn(List.of(new GameEventView(UUID.randomUUID(), taskId, 0, 5, 2,
                "BATTLE", 1, 80, "confirmed", List.of(new GameEventEvidence("OCR", "victory", 2, 1)),
                "CONFIRMED", false, "boss-battle", OffsetDateTime.now())));
        when(workspace.storyboard(taskId)).thenReturn(new StoryboardView("title", "synopsis", true, true,
                List.of(segment(1, 0, 5))));

        NarrativeQualityReport report = new NarrativeConsistencyService(events, workspace, tasks, new ObjectMapper()).inspect(taskId);

        assertThat(report.speakerCoverage()).isZero();
        assertThat(report.upstreamEvidenceReliability()).isEqualTo(1);
        assertThat(report.score()).isEqualTo(92);
        assertThat(report.passed()).isTrue();
    }

    private StoryboardSegmentView segment(int index, double start, double end) {
        return new StoryboardSegmentView(index, start, end, "neutral narration", "subtitle", "cut",
                "BATTLE", "description", 80, false, false);
    }
}
