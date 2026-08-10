package cn.longer233.gamenarrator.event;

import cn.longer233.gamenarrator.script.GeneratedScript;
import cn.longer233.gamenarrator.script.OllamaScriptGenerator;
import cn.longer233.gamenarrator.task.domain.CommentaryStyle;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfirmedEventScriptServiceTest {
    @TempDir Path temp;

    @Test
    void refusesGenerationWhenNoEventWasConfirmed() {
        UUID taskId = UUID.randomUUID();
        VideoTaskRepository tasks = mock(VideoTaskRepository.class);
        GameEventTimelineService events = mock(GameEventTimelineService.class);
        OllamaScriptGenerator generator = mock(OllamaScriptGenerator.class);
        VideoTask task = mock(VideoTask.class);
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));
        when(events.confirmedFacts(taskId)).thenReturn(List.of());
        ConfirmedEventScriptService service = new ConfirmedEventScriptService(
                tasks, events, generator, new ObjectMapper());

        assertThatThrownBy(() -> service.regenerate(taskId))
                .isInstanceOf(IllegalStateException.class);

        verify(generator, never()).generate(any(), any(), any(), any(), any(), any());
        verify(tasks, never()).save(any());
    }

    @Test
    void persistsTaskOnlyAfterConfirmedFactScriptWasGenerated() throws Exception {
        UUID taskId = UUID.randomUUID();
        Path highlights = temp.resolve("highlights.json");
        Path script = temp.resolve("script.json");
        Files.writeString(highlights, "{\"clips\":[]}");
        Files.writeString(script, "{\"qualityReview\":{\"score\":90,\"passed\":true,\"issues\":[],\"summary\":\"ok\"}}");
        VideoTaskRepository tasks = mock(VideoTaskRepository.class);
        GameEventTimelineService events = mock(GameEventTimelineService.class);
        OllamaScriptGenerator generator = mock(OllamaScriptGenerator.class);
        VideoTask task = mock(VideoTask.class);
        GameEventFact fact = new GameEventFact("BOSS_DEFEATED", "Boss defeated", 10, 20, 100);
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));
        when(events.confirmedFacts(taskId)).thenReturn(List.of(fact));
        when(task.getHighlightManifestPath()).thenReturn(highlights.toString());
        when(task.getGameCategory()).thenReturn("ACTION");
        when(task.getCommentaryStyle()).thenReturn(CommentaryStyle.ANIME_THEATER);
        when(task.getTaskBrief()).thenReturn("brief");
        when(generator.generate(eq(highlights), eq("ACTION"), eq("ANIME_THEATER"),
                eq("brief"), any(), eq(List.of(fact))))
                .thenReturn(new GeneratedScript("title", "synopsis", "narration", script.toString(), List.of()));
        ConfirmedEventScriptService service = new ConfirmedEventScriptService(
                tasks, events, generator, new ObjectMapper());

        service.regenerate(taskId);

        verify(task).applyScriptRevision("title", "synopsis", "narration", script.toString(), 0);
        verify(tasks).save(task);
    }
}
