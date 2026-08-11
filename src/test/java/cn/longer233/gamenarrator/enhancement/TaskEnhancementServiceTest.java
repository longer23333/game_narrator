package cn.longer233.gamenarrator.enhancement;

import cn.longer233.gamenarrator.pipeline.EngineTaskContext;
import cn.longer233.gamenarrator.pipeline.TaskWorkflowStateService;
import cn.longer233.gamenarrator.task.domain.TaskStatus;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import cn.longer233.gamenarrator.transcription.PlatformSubtitleReader;
import cn.longer233.gamenarrator.transcription.SubtitleChunkAnalysisService;
import cn.longer233.gamenarrator.transcription.TranscriptionResult;
import cn.longer233.gamenarrator.transcription.Transcriber;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TaskEnhancementServiceTest {
    private final TaskWorkflowStateService state = mock(TaskWorkflowStateService.class);
    private final PlatformSubtitleReader platform = mock(PlatformSubtitleReader.class);
    private final Transcriber transcriber = mock(Transcriber.class);
    private final SubtitleChunkAnalysisService analysis = mock(SubtitleChunkAnalysisService.class);
    private final VideoTaskRepository repository = mock(VideoTaskRepository.class);
    private final TaskEnhancementService service = new TaskEnhancementService(
            state, platform, transcriber, analysis, repository, Runnable::run);

    @Test
    void independentlyTranscribesAndAppliesResultWithoutStartingPipeline() {
        UUID taskId = UUID.randomUUID();
        VideoTask task = mock(VideoTask.class);
        when(task.getStatus()).thenReturn(TaskStatus.READY);
        when(repository.findById(taskId)).thenReturn(Optional.of(task));
        when(state.context(taskId)).thenReturn(context());
        when(platform.read(Path.of("source.mp4"))).thenReturn(null);
        when(transcriber.available()).thenReturn(true);
        TranscriptionResult result = new TranscriptionResult("识别文本", "transcript.txt", "subtitle.srt", "detail.json");
        when(transcriber.transcribe(Path.of("speech.wav"))).thenReturn(result);

        EnhancementJobView started = service.startTranscription(taskId);

        assertThat(started.status()).isEqualTo("RUNNING");
        assertThat(service.status(taskId).getFirst().status()).isEqualTo("COMPLETED");
        verify(state).applyTranscriptionEnhancement(taskId, result);
        verify(analysis).analyze(Path.of("subtitle.srt"));
    }

    @Test
    void keepsFailureInsideEnhancementJob() {
        UUID taskId = UUID.randomUUID();
        VideoTask task = mock(VideoTask.class);
        when(task.getStatus()).thenReturn(TaskStatus.COMPLETED);
        when(repository.findById(taskId)).thenReturn(Optional.of(task));
        when(state.context(taskId)).thenReturn(context());
        when(platform.read(any())).thenReturn(null);
        when(transcriber.available()).thenReturn(false);

        service.startTranscription(taskId);

        assertThat(service.status(taskId).getFirst().status()).isEqualTo("FAILED");
        verify(state, never()).markTranscriptionFailed(any(), any());
        verify(state, never()).applyTranscriptionEnhancement(any(), any());
    }

    @Test
    void rejectsEnhancementWhileMainPipelineIsRunning() {
        UUID taskId = UUID.randomUUID();
        VideoTask task = mock(VideoTask.class);
        when(task.getStatus()).thenReturn(TaskStatus.PROCESSING);
        when(repository.findById(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.startTranscription(taskId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("主流水线正在处理");
    }

    private EngineTaskContext context() {
        return new EngineTaskContext("source.mp4", true, true, false, false, false, false,
                false, false, false, true, "speech.wav", "scenes.json", "", null, null,
                null, null, null, 60.0, 30, "FULL_VIDEO", "ACTION", "ANIME_THEATER", "",
                false, false, false, false, false, false, false);
    }
}
