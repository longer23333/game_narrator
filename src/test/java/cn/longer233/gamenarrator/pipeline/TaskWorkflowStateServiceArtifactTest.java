package cn.longer233.gamenarrator.pipeline;

import cn.longer233.gamenarrator.task.domain.CommentaryStyle;
import cn.longer233.gamenarrator.task.domain.EditingScope;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskWorkflowStateServiceArtifactTest {
    @Test
    void buildsEngineContextFromArtifactLocatorInsteadOfTaskPathColumns() {
        UUID taskId = UUID.randomUUID();
        VideoTask task = mock(VideoTask.class);
        VideoTaskRepository repository = mock(VideoTaskRepository.class);
        TaskArtifactLocator artifacts = mock(TaskArtifactLocator.class);
        when(repository.findById(taskId)).thenReturn(Optional.of(task));
        when(task.getSourceVideoPath()).thenReturn("source.mp4");
        when(task.getAudioCodec()).thenReturn("aac");
        when(task.getEditingScope()).thenReturn(EditingScope.HIGHLIGHTS);
        when(task.getCommentaryStyle()).thenReturn(CommentaryStyle.HUMOROUS);
        when(artifacts.latest(taskId, "EXTRACTED_AUDIO")).thenReturn(Optional.of(Path.of("indexed-audio.wav")));
        when(artifacts.latest(taskId, "SCENE_MANIFEST")).thenReturn(Optional.of(Path.of("indexed-scenes.json")));
        when(artifacts.latest(taskId, "VISION_ANALYSIS")).thenReturn(Optional.of(Path.of("indexed-vision.json")));
        when(artifacts.latest(taskId, "HIGHLIGHT_MANIFEST")).thenReturn(Optional.of(Path.of("indexed-highlights.json")));
        when(artifacts.latest(taskId, "SCRIPT_MANIFEST")).thenReturn(Optional.of(Path.of("indexed-script.json")));
        when(artifacts.latest(taskId, "VOICE_MANIFEST")).thenReturn(Optional.of(Path.of("indexed-voice.json")));
        when(artifacts.latest(taskId, "TIMELINE_MANIFEST")).thenReturn(Optional.of(Path.of("indexed-timeline.json")));

        TaskWorkflowStateService service = new TaskWorkflowStateService(repository, mock(PipelineRunTracker.class),
                mock(cn.longer233.gamenarrator.transcription.TerminologyCorrector.class),
                mock(cn.longer233.gamenarrator.transcription.SpeakerDiarizationService.class),
                mock(ProjectArtifactRegistry.class), artifacts);

        EngineTaskContext context = service.context(taskId);

        assertThat(context.extractedAudioPath()).isEqualTo(Path.of("indexed-audio.wav").toString());
        assertThat(context.sceneManifestPath()).isEqualTo(Path.of("indexed-scenes.json").toString());
        assertThat(context.visualAnalysisPath()).isEqualTo(Path.of("indexed-vision.json").toString());
        assertThat(context.highlightManifestPath()).isEqualTo(Path.of("indexed-highlights.json").toString());
        assertThat(context.generatedScriptPath()).isEqualTo(Path.of("indexed-script.json").toString());
        assertThat(context.voiceManifestPath()).isEqualTo(Path.of("indexed-voice.json").toString());
        assertThat(context.timelinePath()).isEqualTo(Path.of("indexed-timeline.json").toString());
    }
}
