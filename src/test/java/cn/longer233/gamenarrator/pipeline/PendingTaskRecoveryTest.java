package cn.longer233.gamenarrator.pipeline;

import cn.longer233.gamenarrator.diagnostics.SystemDiagnosticsService;
import cn.longer233.gamenarrator.task.domain.CommentaryStyle;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.mockito.Mockito.*;

class PendingTaskRecoveryTest {
    @Test
    void startsOnlyTasksWhoseRequiredToolsAreAvailable() {
        VideoTaskRepository repository = mock(VideoTaskRepository.class);
        VideoTaskEngine engine = mock(VideoTaskEngine.class);
        SystemDiagnosticsService diagnostics = mock(SystemDiagnosticsService.class);
        VideoTask task = new VideoTask("recover", "ACTION", CommentaryStyle.ANIME_THEATER,
                30, "recover task", "source.mp4", false);
        when(repository.findByStatusIn(anyList())).thenReturn(List.of(task));
        when(diagnostics.recoveryBlockers(task)).thenReturn(List.of());

        new PendingTaskRecovery(repository, engine, diagnostics)
                .run(new DefaultApplicationArguments(new String[0]));

        verify(engine).start(task.getId());
    }

    @Test
    void leavesTaskPendingWhenRuntimeToolsAreMissing() {
        VideoTaskRepository repository = mock(VideoTaskRepository.class);
        VideoTaskEngine engine = mock(VideoTaskEngine.class);
        SystemDiagnosticsService diagnostics = mock(SystemDiagnosticsService.class);
        VideoTask task = new VideoTask("recover", "ACTION", CommentaryStyle.ANIME_THEATER,
                30, "recover task", "source.mp4", false);
        when(repository.findByStatusIn(anyList())).thenReturn(List.of(task));
        when(diagnostics.recoveryBlockers(task)).thenReturn(List.of("ffmpeg"));

        new PendingTaskRecovery(repository, engine, diagnostics)
                .run(new DefaultApplicationArguments(new String[0]));

        verifyNoInteractions(engine);
    }

    @Test
    void invalidLegacyTaskDoesNotAbortApplicationRecovery() {
        VideoTaskRepository repository = mock(VideoTaskRepository.class);
        VideoTaskEngine engine = mock(VideoTaskEngine.class);
        SystemDiagnosticsService diagnostics = mock(SystemDiagnosticsService.class);
        VideoTask invalid = new VideoTask("legacy", "ACTION", CommentaryStyle.ANIME_THEATER,
                30, "legacy task", "legacy.mp4", false);
        VideoTask valid = new VideoTask("recover", "ACTION", CommentaryStyle.ANIME_THEATER,
                30, "recover task", "source.mp4", false);
        when(repository.findByStatusIn(anyList())).thenReturn(List.of(invalid, valid));
        when(diagnostics.recoveryBlockers(invalid)).thenThrow(new IllegalStateException("missing stage"));
        when(diagnostics.recoveryBlockers(valid)).thenReturn(List.of());

        new PendingTaskRecovery(repository, engine, diagnostics)
                .run(new DefaultApplicationArguments(new String[0]));

        verify(engine).start(valid.getId());
        verify(engine, never()).start(invalid.getId());
    }
}
