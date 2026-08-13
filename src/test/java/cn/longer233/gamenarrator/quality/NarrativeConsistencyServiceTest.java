package cn.longer233.gamenarrator.quality;

import cn.longer233.gamenarrator.event.GameEventFact;
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
        when(task.getVisualAnalysisPath()).thenReturn(visual.toString());
        when(task.getTranscriptJsonPath()).thenReturn(null);
        when(task.getTranscriptTextPath()).thenReturn(null);
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));
        when(events.confirmedFacts(taskId)).thenReturn(List.of(
                new GameEventFact("BATTLE", "confirmed", 0, 5, 80)));
        when(workspace.storyboard(taskId)).thenReturn(new StoryboardView("title", "synopsis", true, true,
                List.of(segment(1, 0, 5), segment(2, 10, 15), segment(3, 20, 25))));

        NarrativeQualityReport report = new NarrativeConsistencyService(events, workspace, tasks).inspect(taskId);

        assertThat(report.passed()).isFalse();
        assertThat(report.supportedSegmentCount()).isEqualTo(1);
        assertThat(report.evidenceCoverage()).isCloseTo(1d / 3d, org.assertj.core.data.Offset.offset(.001));
        assertThat(report.issues()).extracting(NarrativeQualityReport.Issue::type)
                .contains("EVIDENCE_COVERAGE");
    }

    private StoryboardSegmentView segment(int index, double start, double end) {
        return new StoryboardSegmentView(index, start, end, "neutral narration", "subtitle", "cut",
                "BATTLE", "description", 80, false, false);
    }
}
